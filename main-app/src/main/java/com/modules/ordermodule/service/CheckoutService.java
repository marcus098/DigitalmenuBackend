package com.modules.ordermodule.service;

import com.modules.cardmodule.service.CardService;
import com.modules.common.dto.CardDto;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.ComandCheckout;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.request.CheckoutRequest;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Chiusura del conto in cassa: porta la comanda a COMPLETED e salva sulla comanda quanto è stato incassato
 * (sconto / prezzo finale), poi muove la tessera fedeltà. Gli incassi di cassa si leggono da {@link #summary}.
 */
@Service
public class CheckoutService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    private final MongoComandReadRepository comandRepository;
    private final OrderComandService orderComandService;
    private final CardService cardService;
    private final MongoTemplate mongoTemplate;
    private final AuthenticatedUserProvider auth;

    public CheckoutService(MongoComandReadRepository comandRepository, OrderComandService orderComandService,
                           CardService cardService, MongoTemplate mongoTemplate, AuthenticatedUserProvider auth) {
        this.comandRepository = comandRepository;
        this.orderComandService = orderComandService;
        this.cardService = cardService;
        this.mongoTemplate = mongoTemplate;
        this.auth = auth;
    }

    public record CheckoutResult(ComandCheckout checkout, CardDto card) {}

    public CheckoutResult checkout(String comandId, CheckoutRequest req) {
        long idAgency = auth.getAgencyId();
        long idUser = auth.getUserId();

        ComandJpa comand = comandRepository.findById(comandId)
                .filter(c -> c.getIdAgency() != null && c.getIdAgency() == idAgency)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comanda non trovata"));
        if (comand.getCheckout() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conto già chiuso");
        }
        ComandStatus status = comand.getStatus();
        if (status == ComandStatus.DELETED || status == ComandStatus.AWAIT_PAYMENT || status == ComandStatus.AWAIT_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La comanda non può essere chiusa in questo stato");
        }

        // Cambio stato con tutti gli effetti collaterali soliti (log, slot asporto, eventi, SSE)
        if (status != ComandStatus.COMPLETED) {
            int code = orderComandService.changeStatusToComand(comandId, ComandStatus.COMPLETED).join();
            if (code != 200) throw new ResponseStatusException(HttpStatus.valueOf(code), "Chiusura non riuscita");
        }

        ComandCheckout co = new ComandCheckout();
        co.setSubtotalCents(req.subtotalCents());
        co.setTotalCents(req.totalCents());
        co.setDiscountCents(req.subtotalCents() - req.totalCents());
        co.setDiscountMode(req.discountMode());
        co.setDiscountPct("PCT".equals(req.discountMode()) ? req.discountPct() : null);
        co.setCardDiscountCents(req.cardDiscountCents());
        co.setCardId(req.cardId());
        co.setPaidOnline(Boolean.TRUE.equals(comand.getPaid()));
        co.setClosedAt(LocalDateTime.now());
        co.setClosedBy(idUser);

        // Scrittura condizionale: due chiusure concorrenti non possono registrare (e accreditare) due volte
        Query q = new Query(Criteria.where("_id").is(comandId).and("idAgency").is(idAgency).and("checkout").exists(false));
        if (mongoTemplate.updateFirst(q, new Update().set("checkout", co), ComandStatusUpdater.COLLECTION).getModifiedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conto già chiuso");
        }

        CardDto card = null;
        if (req.cardId() != null) {
            try {
                CardService.CheckoutLoyalty l = cardService.applyCheckout(req.cardId(), idAgency, idUser, req.totalCents(),
                        req.pointsToUse(), req.redeemStamps(), req.earn());
                co.setPointsUsed(l.card().isTypePoints() ? l.claimed() : 0);
                co.setStampRedeemed(!l.card().isTypePoints() && l.claimed() > 0);
                co.setPointsEarned(l.earned());
                card = l.card();
            } catch (Exception e) {
                ErrorLog.logger.error("Movimenti tessera non riusciti alla chiusura della comanda " + comandId, e);
                co.setLoyaltyError(true);
            }
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(comandId)),
                    new Update().set("checkout", co), ComandStatusUpdater.COLLECTION);
        }
        return new CheckoutResult(co, card);
    }

    /** Finestra entro cui una comanda completata e non incassata resta in cassa (turno serale che passa la mezzanotte). */
    private static final long TO_CHECKOUT_WINDOW_HOURS = 36;

    /**
     * Comande servite (COMPLETED, ad es. chiuse dalla pagina Ordini o dalla cucina) ma non ancora incassate in cassa.
     * Escluse quelle già pagate online: non c'è niente da incassare.
     */
    public List<ComandJpa> toCheckout() {
        long idAgency = auth.getAgencyId();
        LocalDateTime since = LocalDateTime.now().minusHours(TO_CHECKOUT_WINDOW_HOURS);
        Query q = new Query(Criteria.where("idAgency").is(idAgency)
                .and("status").is(ComandStatus.COMPLETED.name())
                .and("checkout").exists(false)
                .and("paid").ne(true)
                .and("updatedAt").gte(since))
                .with(org.springframework.data.domain.Sort.by("createdAt"))
                .limit(200);
        return mongoTemplate.find(q, ComandJpa.class, ComandStatusUpdater.COLLECTION);
    }

    /** Incasso di cassa di un giorno. totalCents esclude le comande già pagate online (già nei pagamenti). */
    public record DaySummary(String date, long totalCents, long discountCents, int count) {}

    /**
     * closedAt è salvato nel fuso del server (UTC nei container), come gli altri timestamp delle comande;
     * i giorni del riepilogo sono invece quelli del calendario italiano.
     */
    public List<DaySummary> summary(LocalDate from, LocalDate to) {
        long idAgency = auth.getAgencyId();
        ZoneId server = ZoneId.systemDefault();
        LocalDateTime start = from.atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(server).toLocalDateTime();
        LocalDateTime end = to.plusDays(1).atStartOfDay(BUSINESS_ZONE).withZoneSameInstant(server).toLocalDateTime();
        Query q = new Query(Criteria.where("idAgency").is(idAgency)
                .and("checkout.closedAt").gte(start).lt(end));
        q.fields().include("checkout");
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (ComandJpa c : mongoTemplate.find(q, ComandJpa.class, ComandStatusUpdater.COLLECTION)) {
            ComandCheckout co = c.getCheckout();
            if (co == null || co.getClosedAt() == null) continue;
            LocalDate day = co.getClosedAt().atZone(server).withZoneSameInstant(BUSINESS_ZONE).toLocalDate();
            long[] acc = byDay.computeIfAbsent(day, d -> new long[3]);
            if (!co.isPaidOnline()) acc[0] += co.getTotalCents();
            acc[1] += co.getDiscountCents();
            acc[2]++;
        }
        List<DaySummary> out = new ArrayList<>();
        byDay.forEach((d, a) -> out.add(new DaySummary(d.toString(), a[0], a[1], (int) a[2])));
        return out;
    }
}
