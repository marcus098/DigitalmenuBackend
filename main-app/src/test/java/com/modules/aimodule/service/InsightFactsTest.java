package com.modules.aimodule.service;

import com.modules.aimodule.model.InsightKind;
import com.modules.aimodule.service.InsightDataSource.ComandRow;
import com.modules.aimodule.service.InsightDataSource.MenuProduct;
import com.modules.aimodule.service.InsightDataSource.ReservationRow;
import com.modules.aimodule.service.InsightDataSource.SoldOut;
import com.modules.aimodule.service.InsightFacts.Highlight;
import com.modules.ordermodule.service.TopProductsService.TopProduct;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fatti e highlights calcolati in Java: confronti, formattazione italiana, consigli sul menu. */
class InsightFactsTest {

    private static ComandRow c(String type, String dateTime, long cents) {
        return new ComandRow(type, LocalDateTime.parse(dateTime), cents);
    }

    @Test
    void italianFormatting() {
        assertEquals("1.240 €", InsightFacts.euro(124_000));
        assertEquals("1.241 €", InsightFacts.euro(124_050));
        assertEquals("29,50 €", InsightFacts.euro(2_950));
        assertEquals("1.240,50 €", InsightFacts.euroCents(124_050));
        assertEquals("0,00 €", InsightFacts.euro(0));
        assertEquals("+18%", InsightFacts.signedPct(18));
        assertEquals("-7%", InsightFacts.signedPct(-7));
        assertEquals("0%", InsightFacts.signedPct(0));
        assertEquals("media dei 4 venerdì precedenti", InsightFacts.sameWeekdayReference(DayOfWeek.FRIDAY, 4));
        assertEquals("media dei 2 sabati precedenti", InsightFacts.sameWeekdayReference(DayOfWeek.SATURDAY, 2));
        assertEquals("media delle 4 domeniche precedenti", InsightFacts.sameWeekdayReference(DayOfWeek.SUNDAY, 4));
        assertEquals("il sabato precedente", InsightFacts.sameWeekdayReference(DayOfWeek.SATURDAY, 1));
        assertEquals("la domenica precedente", InsightFacts.sameWeekdayReference(DayOfWeek.SUNDAY, 1));
        assertEquals("20:00–21:00", InsightFacts.hourBand(20));
        assertEquals("23:00–00:00", InsightFacts.hourBand(23));
    }

    @Test
    void dailyComparesWithSameWeekdayOfPreviousWeeks() {
        LocalDate day = LocalDate.of(2026, 10, 30); // venerdì
        List<ComandRow> comands = List.of(
                c("FROM_TABLE", "2026-10-30T20:10:00", 5_000),
                c("FROM_TABLE", "2026-10-30T20:40:00", 4_000),
                c("FROM_TAKEAWAY", "2026-10-30T13:00:00", 3_000),
                c("FROM_TABLE", "2026-10-29T20:00:00", 99_000),   // giovedì: ignorato
                c("FROM_TABLE", "2026-10-23T20:00:00", 10_000),
                c("FROM_TABLE", "2026-10-16T20:00:00", 10_000),
                c("FROM_TABLE", "2026-10-02T20:00:00", 10_000));  // 9 ottobre chiuso: non conta nella media
        List<TopProduct> products = List.of(new TopProduct(1, "Margherita", "Pizze", 5, 3_500),
                new TopProduct(2, "Tiramisù", "Dolci", 2, 1_200));
        List<ReservationRow> res = List.of(
                new ReservationRow(day, "CONFIRMED", 4), new ReservationRow(day, "ARRIVED", 2),
                new ReservationRow(day, "NO_SHOW", 3), new ReservationRow(day, "CANCELLED", 2));
        InsightFacts.Result r = InsightFacts.compute(new InsightFacts.Dataset(InsightPeriod.of(InsightKind.DAILY, day.plusDays(1)),
                comands, products, res, List.of(new SoldOut(2, "Tiramisù", 1)), List.of()));

        assertFalse(r.noData());
        List<Highlight> h = r.highlights();
        assertEquals(List.of("Incasso", "Comande", "Scontrino medio", "Ora di punta", "Prenotazioni", "Più venduto"),
                h.stream().map(Highlight::label).toList());
        assertEquals(new Highlight("Incasso", "120 €", "up", "+20% vs media dei 3 venerdì precedenti"), h.get(0));
        assertEquals(new Highlight("Comande", "3", "up", "+200% vs media dei 3 venerdì precedenti"), h.get(1));
        assertEquals(new Highlight("Scontrino medio", "40,00 €", null, null), h.get(2));
        assertEquals(new Highlight("Ora di punta", "20:00–21:00", null, "2 comande"), h.get(3));
        assertEquals(new Highlight("Prenotazioni", "3", null, "6 coperti · 1 no-show · 1 cancellata"), h.get(4));
        assertEquals(new Highlight("Più venduto", "Margherita", null, "5 porzioni"), h.get(5));

        Map<String, Object> f = r.facts();
        assertEquals("venerdì 30 ottobre 2026", f.get("giorno"));
        assertEquals("120 €", f.get("incasso"));
        assertEquals(3L, f.get("comande"));
        @SuppressWarnings("unchecked") Map<String, Object> cmp = (Map<String, Object>) f.get("confronto");
        assertEquals("100 €", cmp.get("incasso_di_riferimento"));
        assertEquals("+20%", cmp.get("variazione_incasso"));
        assertEquals(List.of("Tiramisù"), f.get("esauriti"));

        assertTrue(r.fallbackText().startsWith("Ieri, venerdì 30 ottobre, hai incassato 120 € con 3 comande"), r.fallbackText());
        assertTrue(r.fallbackText().contains("20% in più rispetto alla media dei 3 venerdì precedenti"), r.fallbackText());
        assertTrue(r.fallbackText().contains("tra le 20:00 e le 21:00"), r.fallbackText());
    }

    @Test
    void dailyWithoutHistoryHasNoTrend() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        InsightFacts.Result r = InsightFacts.compute(new InsightFacts.Dataset(InsightPeriod.of(InsightKind.DAILY, today),
                List.of(c("FROM_WAITER", "2026-10-30T12:00:00", 2_000)), List.of(), List.of(), List.of(), List.of()));
        assertEquals(new Highlight("Incasso", "20,00 €", null, null), r.highlights().get(0));
        assertEquals("nessun dato dei periodi precedenti", r.facts().get("confronto"));
        assertTrue(r.highlights().size() >= 2);
    }

    @Test
    void flatTrendBelowThreshold() {
        Highlight h = InsightFacts.trendHighlight("Incasso", "x", 10_200, 10_000L, "rif");
        assertEquals("flat", h.trend());
        assertEquals("+2% vs rif", h.detail());
        assertEquals("down", InsightFacts.trendHighlight("Incasso", "x", 9_000, 10_000L, "rif").trend());
    }

    @Test
    void noCompletedComandsMeansNoData() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        for (InsightKind k : InsightKind.values()) {
            InsightFacts.Result r = InsightFacts.compute(new InsightFacts.Dataset(InsightPeriod.of(k, today),
                    List.of(c("FROM_TABLE", "2025-01-01T12:00:00", 1_000)), List.of(), List.of(), List.of(), List.of()));
            assertTrue(r.noData(), k.name());
            assertTrue(r.highlights().isEmpty());
            assertNull(r.fallbackText());
        }
    }

    @Test
    void weeklyBestAndWorstDayAndComparison() {
        InsightPeriod p = InsightPeriod.of(InsightKind.WEEKLY, LocalDate.of(2026, 10, 30)); // 19–25 ottobre
        List<ComandRow> comands = List.of(
                c("FROM_TABLE", "2026-10-19T20:00:00", 10_000),
                c("FROM_TABLE", "2026-10-20T20:00:00", 3_000),
                c("FROM_TABLE", "2026-10-24T20:00:00", 20_000),
                c("FROM_TAKEAWAY", "2026-10-24T21:00:00", 5_000),
                c("FROM_TABLE", "2026-10-26T20:00:00", 50_000),   // settimana successiva: ignorata
                c("FROM_TABLE", "2026-10-14T20:00:00", 30_000));
        InsightFacts.Result r = InsightFacts.compute(new InsightFacts.Dataset(p, comands, List.of(), List.of(), List.of(), List.of()));
        List<Highlight> h = r.highlights();
        assertEquals(new Highlight("Incasso", "380 €", "up", "+27% vs la settimana precedente"), h.get(0));
        assertEquals("Comande", h.get(1).label());
        assertEquals("4", h.get(1).value());
        assertEquals(new Highlight("Scontrino medio", "95,00 €", null, null), h.get(2));
        assertEquals(new Highlight("Giorno migliore", "Sabato", null, "250 €"), h.get(3));
        assertEquals(new Highlight("Giorno più debole", "Martedì", null, "30,00 €"), h.get(4));
        assertEquals(3, r.facts().get("giorni_con_comande"));
        assertTrue(r.fallbackText().contains("Il giorno migliore è stato sabato con 250 €, il più debole martedì con 30,00 €"), r.fallbackText());
    }

    @Test
    void menuTipsFindLowSellersSoldOutAndWeakDays() {
        InsightPeriod p = InsightPeriod.of(InsightKind.MENU_TIPS, LocalDate.of(2026, 10, 30)); // 28/9 – 25/10
        assertEquals(LocalDate.of(2026, 9, 28), p.from());
        List<ComandRow> comands = List.of(
                c("FROM_TABLE", "2026-09-29T20:00:00", 1_000),
                c("FROM_TAKEAWAY", "2026-10-03T13:00:00", 9_000),
                c("FROM_HOME", "2026-10-10T19:00:00", 11_000),
                c("FROM_TABLE", "2026-10-23T21:00:00", 5_000));
        List<TopProduct> products = List.of(new TopProduct(1, "Margherita", "Pizze", 10, 7_000),
                new TopProduct(2, "Diavola", "Pizze", 2, 1_600));
        List<MenuProduct> menu = List.of(new MenuProduct(1, "Margherita"), new MenuProduct(2, "Diavola"),
                new MenuProduct(3, "Carbonara"), new MenuProduct(4, "Tiramisù"));
        List<SoldOut> soldOut = List.of(new SoldOut(1, "Margherita", 3), new SoldOut(4, "Tiramisù", 1));
        InsightFacts.Result r = InsightFacts.compute(new InsightFacts.Dataset(p, comands, products, List.of(), soldOut, menu));

        List<Highlight> h = r.highlights();
        assertEquals(List.of("Più venduto", "Poco venduti", "Esauriti spesso", "Giorno più debole", "Asporto e domicilio"),
                h.stream().map(Highlight::label).toList());
        assertEquals(new Highlight("Più venduto", "Margherita", null, "10 porzioni in 28 giorni"), h.get(0));
        assertEquals(new Highlight("Poco venduti", "3 piatti", null, "venduti al massimo 2 volte in 28 giorni"), h.get(1));
        assertEquals(new Highlight("Esauriti spesso", "1 piatto", null, "Margherita"), h.get(2));
        assertEquals(new Highlight("Giorno più debole", "Martedì", null, "media 10,00 €"), h.get(3));
        assertEquals(new Highlight("Asporto e domicilio", "50%", null, "delle comande"), h.get(4));

        @SuppressWarnings("unchecked") List<Map<String, Object>> low = (List<Map<String, Object>>) r.facts().get("poco_venduti_nel_menu");
        assertEquals(List.of("Carbonara", "Tiramisù", "Diavola"), low.stream().map(m -> m.get("nome")).toList());
        assertEquals("50% delle comande", r.facts().get("quota_asporto_e_domicilio"));
        long bullets = r.fallbackText().lines().filter(l -> l.startsWith("- ")).count();
        assertEquals(5, bullets, r.fallbackText());
        assertTrue(r.fallbackText().contains("Il martedì è il giorno più debole"));
    }

    @Test
    void hourBandsWrapAroundMidnight() {
        String[] other = InsightFacts.BANDS[3];
        assertTrue(InsightFacts.inBand(23, other));
        assertTrue(InsightFacts.inBand(2, other));
        assertFalse(InsightFacts.inBand(12, other));
        assertTrue(InsightFacts.inBand(12, InsightFacts.BANDS[0]));
    }

    @Test
    void comandTotalUsesSamePriceRulesAsTopProducts() {
        Document comand = new Document("orders", List.of(
                new Document("products", List.of(
                        new Document("quantity", 2).append("unitPriceCents", 700L),
                        new Document("quantity", 1).append("productOption", new Document("price", 8.5))
                                .append("ingredientsPlus", List.of(new Document("price", 1.0)))))));
        assertEquals(2 * 700 + 950, InsightDataSource.totalCents(comand));
        assertEquals(0, InsightDataSource.totalCents(new Document()));
    }
}
