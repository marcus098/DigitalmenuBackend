package com.modules.ordermodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.*;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.enums.LogOperation;
import com.modules.common.model.variant.VariantGroups;
import com.modules.ordermodule.event.ComandItemsEditedEvent;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.request.EditComandItems;
import com.modules.ordermodule.service.ComandPaymentOperations.ItemsEditGuard;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modifica delle righe di una comanda esistente (PUT /api/orders/{id}/items), per qualsiasi operatore del locale.
 * <ul>
 *   <li>solo negli stati "aperti" ({@link #EDITABLE}) e con il conto non ancora chiuso;</li>
 *   <li>prezzi, nomi e varianti ricalcolati dal catalogo con lo stesso codice della creazione
 *       ({@link OrderComandService#orderList});</li>
 *   <li>mai su comande con un pagamento online incassato o autorizzato (l'importo non si può cambiare senza
 *       rimborso/annullamento): gli intent ancora aperti sul vecchio totale vengono annullati;</li>
 *   <li>scrittura condizionale su stato + versione: due modifiche concorrenti non si sovrascrivono (409);</li>
 *   <li>voce EDITED nello storico con le differenze, evento per lo scontrino "VARIAZIONE ORDINE" in cucina ed evento
 *       Kafka order-updated ({@code edited: true}) per dashboard e app.</li>
 * </ul>
 */
@Service
public class ComandItemsEditService {

    /** Stati in cui le righe si possono ancora cambiare (non COMPLETED/DELETED/AWAIT_PAYMENT). */
    public static final List<ComandStatus> EDITABLE = List.of(
            ComandStatus.AWAIT, ComandStatus.PENDING, ComandStatus.PROGRESS, ComandStatus.AWAIT_APPROVAL);

    public static final String CODE_EMPTY = "EMPTY";
    public static final String CODE_NOT_FOUND = "NOT_FOUND";
    public static final String CODE_NOT_EDITABLE = "NOT_EDITABLE";
    public static final String CODE_VERSION_CONFLICT = "VERSION_CONFLICT";
    public static final String CODE_PAID_ONLINE = "PAID_ONLINE";
    public static final String CODE_PAYMENT_AUTHORIZED = "PAYMENT_AUTHORIZED";
    public static final String CODE_PAYMENT_IN_PROGRESS = "PAYMENT_IN_PROGRESS";

    public static final String MSG_EMPTY = "L'ordine non può restare vuoto: per annullarlo eliminalo";
    public static final String MSG_VERSION_CONFLICT =
            "L'ordine è stato appena modificato da un altro utente: ricarica e riprova";
    public static final String MSG_PAID_ONLINE =
            "Ordine già pagato online: per cambiarlo annullalo e rifallo, o rimborsa dalla cassa";
    public static final String MSG_PAYMENT_AUTHORIZED =
            "Ordine con pagamento online autorizzato: per cambiarlo annulla il pagamento dalla cassa (nessuna commissione) "
                    + "o annulla l'ordine e rifallo";
    public static final String MSG_PAYMENT_IN_PROGRESS =
            "Un pagamento online di questo ordine è in corso: riprova tra qualche secondo";

    /** Esito: comanda aggiornata, nuovo totale (prodotti + consegna) e differenze (vuote = nessun cambiamento). */
    public record EditResult(Comand comand, long totalCents, List<ComandItemChange> changes) {
        public boolean changed() { return !changes.isEmpty(); }
    }

    private final MongoComandReadRepository comandRepository;
    private final MongoComandLogRepository logRepository;
    private final OrderComandService orderComandService;
    private final MongoTemplate mongoTemplate;
    private final AuthenticatedUserProvider auth;
    /** Lazy: PaymentService dipende (tramite il calcolo del totale) dai service delle comande. */
    private final ObjectProvider<ComandPaymentOperations> paymentOperations;
    private final ApplicationEventPublisher eventPublisher;
    private final TakeawaySlotService takeawaySlotService;

    public ComandItemsEditService(MongoComandReadRepository comandRepository, MongoComandLogRepository logRepository,
                                  OrderComandService orderComandService, MongoTemplate mongoTemplate,
                                  AuthenticatedUserProvider auth, ObjectProvider<ComandPaymentOperations> paymentOperations,
                                  ApplicationEventPublisher eventPublisher, TakeawaySlotService takeawaySlotService) {
        this.comandRepository = comandRepository;
        this.logRepository = logRepository;
        this.orderComandService = orderComandService;
        this.mongoTemplate = mongoTemplate;
        this.auth = auth;
        this.paymentOperations = paymentOperations;
        this.eventPublisher = eventPublisher;
        this.takeawaySlotService = takeawaySlotService;
    }

    /**
     * @throws OrderRejectedException 400 (vuoto / payload non valido), 404 (non trovata o di un altro locale),
     *                                409 (stato non modificabile, versione cambiata, pagamento online)
     */
    public EditResult editItems(String comandId, EditComandItems req) {
        if (req == null || req.lineCount() == 0) throw new OrderRejectedException(400, CODE_EMPTY, MSG_EMPTY);
        long idAgency = auth.getAgencyId();
        ComandTakenBy actor = ComandHistory.currentActor(auth);
        long idUser = actor.getId() != null ? actor.getId() : 0L;

        ComandJpa c = comandRepository.findById(comandId)
                .filter(x -> x.getIdAgency() != null && x.getIdAgency() == idAgency)
                .orElseThrow(() -> new OrderRejectedException(404, CODE_NOT_FOUND, "Comanda non trovata"));
        requireEditable(c);
        long version = c.getVersion();
        if (req.getExpectedVersion() != null && req.getExpectedVersion() != version) {
            throw new OrderRejectedException(409, CODE_VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }

        // Stesso percorso della creazione: catalogo del locale, quantità 1..50, varianti, prezzi snapshot
        List<Order> rebuilt = orderComandService.orderList(idAgency, idUser, req.getOrders(), comandId);
        List<Order> newOrders = keepOrderIdentity(c.getOrders(), rebuilt);
        List<ComandItemChange> changes = ComandItemDiff.diff(c.getOrders(), newOrders);
        if (changes.isEmpty()) return new EditResult(c, totalCents(c.getOrders(), c), changes);

        requireNoOnlinePayment(c);

        LocalDateTime now = LocalDateTime.now();
        Criteria crit = Criteria.where("_id").is(comandId).and("idAgency").is(idAgency)
                .and("status").in(EDITABLE.stream().map(Enum::name).toList())
                .and("checkout").exists(false)
                .and("paid").ne(true)
                .and("paymentAuthorized").ne(true);
        // le comande precedenti non hanno il campo version: $in [0, null] li comprende
        crit = version == 0 ? crit.and("version").in(0L, null) : crit.and("version").is(version);
        Update u = new Update().set("orders", newOrders).set("updatedAt", now).inc("version", 1);
        ComandHistory.push(u, ComandHistoryEntry.edited(actor, changes));
        if (mongoTemplate.updateFirst(new Query(crit), u, ComandStatusUpdater.COLLECTION).getModifiedCount() == 0) {
            // Qualcosa è cambiato nel frattempo: rilegge per dare il motivo giusto
            ComandJpa fresh = comandRepository.findById(comandId).orElse(null);
            if (fresh == null) throw new OrderRejectedException(404, CODE_NOT_FOUND, "Comanda non trovata");
            requireEditable(fresh);
            requireNoOnlinePayment(fresh);
            throw new OrderRejectedException(409, CODE_VERSION_CONFLICT, MSG_VERSION_CONFLICT);
        }

        ComandJpa updated = comandRepository.findById(comandId).orElseGet(() -> {
            c.setOrders(newOrders);
            c.setVersion(version + 1);
            c.setUpdatedAt(now);
            return c;
        });
        afterEdit(c, updated, changes, actor, idUser, idAgency, now);
        return new EditResult(updated, totalCents(updated.getOrders(), updated), changes);
    }

    /** Effetti collaterali (best effort: la modifica è già salvata). */
    private void afterEdit(ComandJpa before, ComandJpa after, List<ComandItemChange> changes, ComandTakenBy actor,
                           long idUser, long idAgency, LocalDateTime now) {
        if (before.getComandWaiterType() == ComandWaiterType.TAKE_AWAY && takeawaySlotService != null) {
            takeawaySlotService.adjustProducts(idAgency, before.getTime(), before.getCreatedAt(),
                    countProducts(after.getOrders()) - countProducts(before.getOrders()));
        }
        try {
            logRepository.save(new EntityLog<>(LogOperation.UPDATE, before, after,
                    "function editItems for comand " + before.getId(), idUser, idAgency));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore salvataggio log modifica comanda " + before.getId(), e);
        }
        try {
            eventPublisher.publishEvent(new ComandItemsEditedEvent(before.getId(), String.valueOf(idAgency), changes,
                    actor.getName(), now));
        } catch (Exception e) {
            ErrorLog.logger.error("Errore pubblicazione ComandItemsEditedEvent comandId=" + before.getId(), e);
        }
        orderComandService.publishOrderEdited(after);
    }

    static void requireEditable(Comand c) {
        if (c.getCheckout() != null) {
            throw new OrderRejectedException(409, CODE_NOT_EDITABLE, "Conto già chiuso in cassa: l'ordine non si può più modificare");
        }
        ComandStatus s = c.getStatus();
        if (s != null && EDITABLE.contains(s)) return;
        String msg = s == null ? "L'ordine non si può modificare in questo stato" : switch (s) {
            case COMPLETED -> "Ordine già completato: non si può più modificare";
            case DELETED -> "Ordine annullato: non si può modificare";
            case AWAIT_PAYMENT -> "Ordine in attesa del pagamento online: non si può modificare";
            default -> "L'ordine non si può modificare in questo stato";
        };
        throw new OrderRejectedException(409, CODE_NOT_EDITABLE, msg);
    }

    /** Pagamenti online: flag della comanda (webhook già arrivato) + stato dei pagamenti presso il provider. */
    private void requireNoOnlinePayment(Comand c) {
        if (Boolean.TRUE.equals(c.getPaymentAuthorized())) {
            throw new OrderRejectedException(409, CODE_PAYMENT_AUTHORIZED, MSG_PAYMENT_AUTHORIZED);
        }
        if (Boolean.TRUE.equals(c.getPaid())) throw new OrderRejectedException(409, CODE_PAID_ONLINE, MSG_PAID_ONLINE);
        ComandPaymentOperations ops = paymentOperations == null ? null : paymentOperations.getIfAvailable();
        if (ops == null) return;
        ItemsEditGuard guard;
        try {
            guard = ops.prepareForItemsEdit(c.getId());
        } catch (Exception e) {
            ErrorLog.logger.error("Errore controllo pagamenti prima della modifica della comanda " + c.getId(), e);
            guard = ItemsEditGuard.BUSY;
        }
        switch (guard) {
            case CAPTURED -> throw new OrderRejectedException(409, CODE_PAID_ONLINE, MSG_PAID_ONLINE);
            case AUTHORIZED -> throw new OrderRejectedException(409, CODE_PAYMENT_AUTHORIZED, MSG_PAYMENT_AUTHORIZED);
            case BUSY -> throw new OrderRejectedException(409, CODE_PAYMENT_IN_PROGRESS, MSG_PAYMENT_IN_PROGRESS);
            default -> { }
        }
    }

    /**
     * Le righe ricostruite mantengono id, creazione e autore dei "giri" esistenti (stessa posizione); i giri vuoti
     * vengono scartati.
     */
    static List<Order> keepOrderIdentity(List<Order> old, List<Order> rebuilt) {
        List<Order> out = new ArrayList<>();
        int i = 0;
        for (Order o : rebuilt) {
            if (o == null || o.getProducts() == null || o.getProducts().isEmpty()) continue;
            Order prev = old != null && i < old.size() ? old.get(i) : null;
            if (prev != null) {
                if (prev.getId() != null) o.setId(prev.getId());
                if (prev.getCreatedAt() != null) o.setCreatedAt(prev.getCreatedAt());
                if (prev.getUserId() != null) o.setUserId(prev.getUserId());
            }
            out.add(o);
            i++;
        }
        return out;
    }

    /** Totale come per il pagamento online: snapshot unitPriceCents (o opzione + varianti + extra) + consegna. */
    static long totalCents(List<Order> orders, Comand c) {
        long total = c.deliveryFeeCents();
        if (orders == null) return total;
        for (Order o : orders) {
            if (o == null || o.getProducts() == null) continue;
            for (ProductToOrder p : o.getProducts()) {
                if (p == null) continue;
                Long unit = p.getUnitPriceCents();
                if (unit == null) unit = VariantGroups.unitPriceCents(p.getProductOption(), p.getSelectedVariants(), p.getIngredientsPlus());
                total += unit * Math.max(0, p.getQuantity());
            }
        }
        return total;
    }

    private static int countProducts(List<Order> orders) {
        if (orders == null) return 0;
        return orders.stream()
                .mapToInt(o -> o == null || o.getProducts() == null ? 0
                        : o.getProducts().stream().mapToInt(ProductToOrder::getQuantity).sum())
                .sum();
    }
}
