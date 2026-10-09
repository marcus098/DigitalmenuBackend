package com.modules.mainapp.superadmin;

import com.modules.common.logs.errorlog.ErrorLog;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Statistiche ordini per il pannello superadmin calcolate con aggregazioni Mongo (mai caricando le comande).
 * In caso di errore Mongo restituisce valori vuoti: il pannello deve funzionare comunque.
 */
@Component
public class OrderStatsProvider {

    static final String COMAND_COLLECTION = "comand";

    public record AgencyOrderStats(Date lastOrderAt, long ordersLast30Days) {
    }

    private final MongoTemplate mongoTemplate;

    public OrderStatsProvider(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Ultimo ordine e ordini degli ultimi 30 giorni per locale. {@code agencyIds} null = tutti i locali. */
    public Map<Long, AgencyOrderStats> statsByAgency(Collection<Long> agencyIds) {
        Map<Long, AgencyOrderStats> out = new HashMap<>();
        try {
            Date since = toDate(LocalDateTime.now().minusDays(30));
            List<AggregationOperation> ops = new ArrayList<>();
            if (agencyIds != null) {
                ops.add(Aggregation.match(Criteria.where("idAgency").in(agencyIds)));
            }
            ops.add(Aggregation.group("idAgency")
                    .max("createdAt").as("lastOrderAt")
                    .sum(ConditionalOperators.when(ComparisonOperators.valueOf("createdAt").greaterThanEqualToValue(since))
                            .then(1).otherwise(0)).as("last30"));
            for (Document d : mongoTemplate.aggregate(Aggregation.newAggregation(ops), COMAND_COLLECTION, Document.class)) {
                Object id = d.get("_id");
                if (!(id instanceof Number n)) continue;
                Object last = d.get("lastOrderAt");
                Object count = d.get("last30");
                out.put(n.longValue(), new AgencyOrderStats(last instanceof Date dt ? dt : null,
                        count instanceof Number c ? c.longValue() : 0));
            }
        } catch (Exception e) {
            ErrorLog.logger.error("Superadmin: errore statistiche ordini", e);
        }
        return out;
    }

    /** Ordini creati da oggi a mezzanotte (fuso del server), tutti i locali. */
    public long ordersToday() {
        return countSince(toDate(LocalDate.now().atStartOfDay()));
    }

    public long ordersLast30Days() {
        return countSince(toDate(LocalDateTime.now().minusDays(30)));
    }

    private long countSince(Date since) {
        try {
            return mongoTemplate.count(new Query(Criteria.where("createdAt").gte(since)), COMAND_COLLECTION);
        } catch (Exception e) {
            ErrorLog.logger.error("Superadmin: errore conteggio ordini", e);
            return 0;
        }
    }

    /** Le comande salvano createdAt come LocalDateTime, convertito da Spring in Date con il fuso del server. */
    private static Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}
