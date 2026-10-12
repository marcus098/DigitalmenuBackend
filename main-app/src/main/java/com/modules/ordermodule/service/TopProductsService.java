package com.modules.ordermodule.service;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.enums.ComandStatus;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.bson.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Prodotti più venduti in un periodo: aggregazione Mongo sulle comande COMPLETED (escluse quindi le DELETED,
 * rifiutate o mai pagate), raggruppate per prodotto. Il ricavo usa lo stesso prezzo dell'app/web:
 * unitPriceCents (snapshot server) oppure, per le comande più vecchie, prezzo opzione + extra.
 */
@Service
public class TopProductsService {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    static final String INDEX_NAME = "ix_agency_status_created";

    private final MongoTemplate mongoTemplate;
    private final AuthenticatedUserProvider auth;

    public TopProductsService(MongoTemplate mongoTemplate, AuthenticatedUserProvider auth) {
        this.mongoTemplate = mongoTemplate;
        this.auth = auth;
    }

    public record TopProduct(long idProduct, String name, String categoryName, long quantity, long revenueCents) {}

    /** Indice per il $match iniziale (locale + stato + data di creazione). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        try {
            mongoTemplate.indexOps(ComandStatusUpdater.COLLECTION).ensureIndex(new Index()
                    .on("idAgency", Sort.Direction.ASC)
                    .on("status", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.ASC)
                    .named(INDEX_NAME));
        } catch (Exception e) {
            ErrorLog.logger.error("Impossibile creare l'indice " + INDEX_NAME + " su comand", e);
        }
    }

    /**
     * Validazione dei parametri: from/to obbligatori (estremi inclusi, massimo un anno), limit 1..100 (default 20).
     *
     * @throws IllegalArgumentException parametri non validi (→ 400)
     */
    static int validate(LocalDate from, LocalDate to, Integer limit) {
        if (from == null || to == null) throw new IllegalArgumentException("Indicare from e to");
        if (to.isBefore(from) || from.plusYears(1).isBefore(to)) throw new IllegalArgumentException("Intervallo non valido");
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1 || limit > MAX_LIMIT) throw new IllegalArgumentException("limit deve essere tra 1 e " + MAX_LIMIT);
        return limit;
    }

    public List<TopProduct> topProducts(LocalDate from, LocalDate to, Integer limit) {
        int max = validate(from, to, limit);
        long idAgency = auth.getAgencyId();
        // createdAt è un LocalDateTime salvato da Spring come Date nel fuso del server (Europe/Rome):
        // stessa conversione per gli estremi del periodo
        ZoneId server = ZoneId.systemDefault();
        Date start = toDate(from.atStartOfDay(), server);
        Date end = toDate(to.plusDays(1).atStartOfDay(), server);

        String p = "$orders.products.";
        List<AggregationOperation> stages = List.of(
                stage(new Document("$match", new Document("idAgency", idAgency)
                        .append("status", ComandStatus.COMPLETED.name())
                        .append("createdAt", new Document("$gte", start).append("$lt", end)))),
                stage(new Document("$project", new Document("orders.products", 1))),
                stage(new Document("$unwind", "$orders")),
                stage(new Document("$unwind", "$orders.products")),
                // Raggruppo anche per prezzo: il ricavo si calcola in Java con gli stessi arrotondamenti dell'app
                stage(new Document("$group", new Document("_id", new Document("idProduct", p + "idProduct")
                        .append("name", p + "productName")
                        .append("categoryName", p + "categoryName")
                        .append("unitPriceCents", p + "unitPriceCents")
                        .append("optionPrice", p + "productOption.price")
                        .append("extras", p + "ingredientsPlus.price"))
                        .append("quantity", new Document("$sum", p + "quantity")))));
        List<Document> rows = mongoTemplate.aggregate(Aggregation.newAggregation(stages),
                ComandStatusUpdater.COLLECTION, Document.class).getMappedResults();
        return reduce(rows, max);
    }

    private static AggregationOperation stage(Document d) {
        return context -> d;
    }

    private static Date toDate(LocalDateTime t, ZoneId zone) {
        return Date.from(t.atZone(zone).toInstant());
    }

    /**
     * Unisce le righe dell'aggregazione per idProduct (nome/categoria della variante più venduta, ad es. dopo una
     * rinomina), ordina per quantità e poi ricavo decrescenti e taglia a {@code limit}.
     */
    static List<TopProduct> reduce(List<Document> rows, int limit) {
        Map<Long, long[]> totals = new HashMap<>();          // idProduct -> [quantità, ricavo]
        Map<Long, Document> labels = new HashMap<>();        // idProduct -> chiave con quantità maggiore
        Map<Long, Long> labelQty = new HashMap<>();
        for (Document row : rows) {
            Document key = row.get("_id", Document.class);
            if (key == null || !(key.get("idProduct") instanceof Number id)) continue;
            long qty = Math.max(0, num(row.get("quantity")));
            if (qty == 0) continue;
            long[] acc = totals.computeIfAbsent(id.longValue(), k -> new long[2]);
            acc[0] += qty;
            acc[1] += unitCents(key) * qty;
            if (qty > labelQty.getOrDefault(id.longValue(), 0L)) {
                labelQty.put(id.longValue(), qty);
                labels.put(id.longValue(), key);
            }
        }
        List<TopProduct> out = new ArrayList<>();
        totals.forEach((id, acc) -> {
            Document l = labels.get(id);
            out.add(new TopProduct(id, l.getString("name"), l.getString("categoryName"), acc[0], acc[1]));
        });
        out.sort(Comparator.comparingLong(TopProduct::quantity).reversed()
                .thenComparing(Comparator.comparingLong(TopProduct::revenueCents).reversed())
                .thenComparingLong(TopProduct::idProduct));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Come comandTotalCents dell'app: unitPriceCents, altrimenti round(opzione*100) + somma di round(extra*100). */
    static long unitCents(Document key) {
        if (key.get("unitPriceCents") instanceof Number n) return n.longValue();
        long cents = key.get("optionPrice") instanceof Number n ? Math.max(0, Math.round(n.doubleValue() * 100)) : 0;
        if (key.get("extras") instanceof List<?> extras) {
            for (Object e : extras) {
                if (e instanceof Number n) cents += Math.max(0, Math.round(n.doubleValue() * 100));
            }
        }
        return cents;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
