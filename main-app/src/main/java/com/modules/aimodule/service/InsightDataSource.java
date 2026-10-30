package com.modules.aimodule.service;

import com.modules.common.model.EntityLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.reservation.entity.ReservationJpa;
import com.modules.mainapp.reservation.repository.ReservationRepository;
import com.modules.ordermodule.service.ComandStatusUpdater;
import com.modules.ordermodule.service.TopProductsService;
import com.modules.productmodule.model.ProductJpa;
import com.modules.productmodule.repository.ProductRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Lettura dei dati grezzi per i riepiloghi AxiomAI. Ogni metodo riceve il locale in modo esplicito
 * (il job notturno gira senza utente autenticato). Le date sono giorni del calendario italiano, estremi inclusi.
 * <p>
 * createdAt delle comande e loggedAt dei log sono LocalDateTime salvati da Spring come Date nel fuso della JVM
 * (Europe/Rome, vedi BusinessTime): stessa conversione di {@link TopProductsService}.
 */
@Component
public class InsightDataSource {

    /** Nota scritta da ProductService.setAvailable quando un piatto viene segnato come esaurito. */
    static final String SOLD_OUT_NOTE = "function setAvailable, value false";

    private final MongoTemplate mongoTemplate;
    private final TopProductsService topProductsService;
    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;

    public InsightDataSource(MongoTemplate mongoTemplate, TopProductsService topProductsService,
                             ReservationRepository reservationRepository, ProductRepository productRepository) {
        this.mongoTemplate = mongoTemplate;
        this.topProductsService = topProductsService;
        this.reservationRepository = reservationRepository;
        this.productRepository = productRepository;
    }

    /** Comanda completata: tipo, ora di creazione e totale lordo (somma prezzo unitario × quantità dei prodotti). */
    public record ComandRow(String type, LocalDateTime createdAt, long totalCents) {}

    public record ReservationRow(LocalDate date, String status, int partySize) {}

    /** Quante volte un piatto è stato segnato come esaurito nel periodo. */
    public record SoldOut(long idProduct, String name, int times) {}

    public record MenuProduct(long idProduct, String name) {}

    /** Comande COMPLETED del locale create nel periodo. */
    public List<ComandRow> comands(long idAgency, LocalDate from, LocalDate to) {
        Query q = new Query(Criteria.where("idAgency").is(idAgency)
                .and("status").is(ComandStatus.COMPLETED.name())
                .and("createdAt").gte(start(from)).lt(start(to.plusDays(1))));
        q.fields().include("type").include("createdAt")
                .include("orders.products.quantity")
                .include("orders.products.unitPriceCents")
                .include("orders.products.productOption.price")
                .include("orders.products.ingredientsPlus.price");
        List<ComandRow> out = new ArrayList<>();
        for (Document d : mongoTemplate.find(q, Document.class, ComandStatusUpdater.COLLECTION)) {
            LocalDateTime created = toLocal(d.get("createdAt"));
            if (created == null) continue;
            out.add(new ComandRow(d.get("type") == null ? null : d.get("type").toString(), created, totalCents(d)));
        }
        return out;
    }

    /** Totale lordo della comanda con le stesse regole di prezzo dei "più venduti". */
    static long totalCents(Document comand) {
        long total = 0;
        if (!(comand.get("orders") instanceof List<?> orders)) return 0;
        for (Object o : orders) {
            if (!(o instanceof Document order) || !(order.get("products") instanceof List<?> products)) continue;
            for (Object p : products) {
                if (!(p instanceof Document product)) continue;
                long qty = product.get("quantity") instanceof Number n ? Math.max(0, n.longValue()) : 0;
                total += TopProductsService.lineUnitCents(product) * qty;
            }
        }
        return total;
    }

    /** Tutti i prodotti venduti nel periodo (ordinati per quantità). */
    public List<TopProductsService.TopProduct> products(long idAgency, LocalDate from, LocalDate to) {
        return topProductsService.topProducts(idAgency, from, to, Integer.MAX_VALUE);
    }

    public List<ReservationRow> reservations(long idAgency, LocalDate from, LocalDate to) {
        List<ReservationRow> out = new ArrayList<>();
        for (ReservationJpa r : reservationRepository
                .findByIdAgencyAndReservationDateBetweenOrderByReservationDateAscReservationTimeAsc(idAgency, from, to)) {
            out.add(new ReservationRow(r.getReservationDate(), r.getStatus(), r.getPartySize()));
        }
        return out;
    }

    /** Eventi "esaurito" (setAvailable false) del periodo, raggruppati per piatto, dal più frequente. */
    public List<SoldOut> soldOut(long idAgency, LocalDate from, LocalDate to) {
        Query q = new Query(Criteria.where("idAgency").is(idAgency)
                .and("note").is(SOLD_OUT_NOTE)
                .and("loggedAt").gte(start(from)).lt(start(to.plusDays(1))));
        q.fields().include("newValue").include("oldValue");
        String collection = mongoTemplate.getCollectionName(EntityLog.class);
        Map<Long, String> names = new HashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        for (Document d : mongoTemplate.find(q, Document.class, collection == null ? "entityLog" : collection)) {
            Document product = d.get("newValue") instanceof Document n ? n
                    : d.get("oldValue") instanceof Document o ? o : null;
            if (product == null) continue;
            // L'id del ProductDto annidato può essere salvato come "_id" o "id"
            Object id = product.get("_id") != null ? product.get("_id") : product.get("id");
            if (!(id instanceof Number n)) continue;
            counts.merge(n.longValue(), 1, Integer::sum);
            if (product.get("name") instanceof String name && !name.isBlank()) names.put(n.longValue(), name);
        }
        List<SoldOut> out = new ArrayList<>();
        counts.forEach((id, c) -> out.add(new SoldOut(id, names.getOrDefault(id, "Piatto " + id), c)));
        out.sort(Comparator.comparingInt(SoldOut::times).reversed().thenComparing(SoldOut::name));
        return out;
    }

    /** Piatti attualmente nel menu (non eliminati e disponibili). */
    public List<MenuProduct> menuProducts(long idAgency) {
        List<MenuProduct> out = new ArrayList<>();
        for (ProductJpa p : productRepository.findAllByDeletedAndIdAgency(false, idAgency)) {
            if (p.getId() == null || !p.isAvailable()) continue;
            out.add(new MenuProduct(p.getId(), p.getName()));
        }
        return out;
    }

    /** Locali con almeno una comanda completata nel periodo (per il job notturno). */
    public Set<Long> agenciesWithCompletedComands(LocalDate from, LocalDate to) {
        Query q = new Query(Criteria.where("status").is(ComandStatus.COMPLETED.name())
                .and("createdAt").gte(start(from)).lt(start(to.plusDays(1))));
        Set<Long> out = new TreeSet<>();
        for (Object id : mongoTemplate.findDistinct(q, "idAgency", ComandStatusUpdater.COLLECTION, Object.class)) {
            if (id instanceof Number n) out.add(n.longValue());
        }
        return out;
    }

    private static Date start(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocal(Object value) {
        if (value instanceof Date d) return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        if (value instanceof LocalDateTime t) return t;
        return null;
    }
}
