package com.modules.ordermodule.service;

import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Più venduti: validazione, calcolo del ricavo (come l'app) e riduzione delle righe dell'aggregazione. */
class TopProductsServiceTest {

    private static Document row(long id, String name, Long unitCents, Double option, List<Double> extras, int qty) {
        Document key = new Document("idProduct", id).append("name", name).append("categoryName", "Pizze")
                .append("unitPriceCents", unitCents).append("optionPrice", option).append("extras", extras);
        return new Document("_id", key).append("quantity", qty);
    }

    @Test
    void validation() {
        LocalDate d = LocalDate.of(2026, 10, 12);
        assertEquals(TopProductsService.DEFAULT_LIMIT, TopProductsService.validate(d, d, null));
        assertEquals(5, TopProductsService.validate(d.minusYears(1), d, 5));
        assertThrows(IllegalArgumentException.class, () -> TopProductsService.validate(null, d, null));
        assertThrows(IllegalArgumentException.class, () -> TopProductsService.validate(d, d.minusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> TopProductsService.validate(d.minusYears(1).minusDays(1), d, null));
        assertThrows(IllegalArgumentException.class, () -> TopProductsService.validate(d, d, 0));
        assertThrows(IllegalArgumentException.class, () -> TopProductsService.validate(d, d, TopProductsService.MAX_LIMIT + 1));
    }

    @Test
    void unitPriceFallsBackToOptionPlusExtras() {
        assertEquals(1200, TopProductsService.unitCents(row(1, "a", 1200L, 9.0, List.of(1.0), 1).get("_id", Document.class)));
        // 8.5 + 0.335 (→ 34 centesimi) + 1.0, null ignorati: 850 + 34 + 100
        assertEquals(984, TopProductsService.unitCents(
                row(1, "a", null, 8.5, Arrays.asList(0.335, null, 1.0), 1).get("_id", Document.class)));
        assertEquals(0, TopProductsService.unitCents(row(1, "a", null, null, null, 1).get("_id", Document.class)));
    }

    @Test
    void mergesByProductAndSortsByQuantity() {
        List<Document> rows = List.of(
                row(1, "Margherita", 700L, null, null, 3),
                row(1, "Margherita", null, 7.0, List.of(1.5), 2),     // vecchia comanda: 850 cent
                row(1, "Pizza Margherita", 700L, null, null, 1),      // rinominata: stessa riga
                row(2, "Diavola", 900L, null, null, 6),
                row(3, "Acqua", 150L, null, null, 6),
                row(4, "Vuoto", 100L, null, null, 0));
        List<TopProductsService.TopProduct> top = TopProductsService.reduce(rows, 10);
        assertEquals(3, top.size());
        assertEquals(new TopProductsService.TopProduct(2, "Diavola", "Pizze", 6, 5400), top.get(0));
        assertEquals(new TopProductsService.TopProduct(1, "Margherita", "Pizze", 6, 3 * 700 + 2 * 850 + 700), top.get(1));
        assertEquals(3L, top.get(2).idProduct());
        assertEquals(2, TopProductsService.reduce(rows, 2).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void runsAggregationOnCompletedComandsOfTheAgency() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getAgencyId()).thenReturn(7L);
        when(mongo.aggregate(any(Aggregation.class), eq(ComandStatusUpdater.COLLECTION), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(row(2, "Diavola", 900L, null, null, 2)), new Document()));

        List<TopProductsService.TopProduct> top = new TopProductsService(mongo, auth)
                .topProducts(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 12), null);
        assertEquals(List.of(new TopProductsService.TopProduct(2, "Diavola", "Pizze", 2, 1800)), top);

        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongo).aggregate(agg.capture(), eq(ComandStatusUpdater.COLLECTION), eq(Document.class));
        Document match = agg.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).get(0).get("$match", Document.class);
        assertEquals(7L, match.get("idAgency"));
        assertEquals("COMPLETED", match.get("status"));
        Document created = match.get("createdAt", Document.class);
        assertEquals(java.sql.Timestamp.valueOf(LocalDate.of(2026, 10, 6).atStartOfDay()).getTime(),
                created.get("$gte", Date.class).getTime());
        assertEquals(java.sql.Timestamp.valueOf(LocalDate.of(2026, 10, 13).atStartOfDay()).getTime(),
                created.get("$lt", Date.class).getTime());
    }
}
