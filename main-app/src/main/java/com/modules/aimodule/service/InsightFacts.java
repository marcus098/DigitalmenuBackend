package com.modules.aimodule.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modules.aimodule.service.InsightDataSource.ComandRow;
import com.modules.aimodule.service.InsightDataSource.MenuProduct;
import com.modules.aimodule.service.InsightDataSource.ReservationRow;
import com.modules.aimodule.service.InsightDataSource.SoldOut;
import com.modules.ordermodule.service.TopProductsService.TopProduct;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calcolo deterministico dei fatti di un riepilogo AxiomAI (nessun accesso ai dati: riceve un {@link Dataset}).
 * <p>
 * <b>Definizione di incasso</b> (unica per tutti i riepiloghi): somma di prezzo unitario × quantità dei prodotti
 * delle comande COMPLETED create nel periodo, con le stesse regole di prezzo dei "più venduti"
 * ({@code TopProductsService.unitCents}). È quindi l'incasso lordo, prima degli sconti di cassa, e include
 * anche le comande pagate online. Le comande sono attribuite al giorno della loro creazione (ora italiana).
 * <p>
 * I valori nei {@code facts} sono già formattati in italiano: l'AI li deve solo riportare, non ricalcolare.
 */
public final class InsightFacts {

    private InsightFacts() {}

    static final Locale IT = Locale.ITALIAN;
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", IT);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", IT);
    /** Settimane / giorni precedenti usati come confronto. */
    static final int COMPARISON_WEEKS = 4;
    /** Venduto al massimo questo numero di volte in 28 giorni = "poco venduto". */
    static final int LOW_SELLER_MAX = 2;
    /** Esaurito almeno questo numero di volte in 28 giorni = "esaurito spesso". */
    static final int SOLD_OUT_MIN = 2;
    /** Sotto questa variazione percentuale (in valore assoluto) il trend è "flat". */
    static final int FLAT_THRESHOLD_PCT = 3;

    /**
     * Dati grezzi. {@code comands} copre periodo + finestra di confronto (DAILY: 28 giorni prima, WEEKLY: 4 settimane
     * prima); gli altri campi riguardano solo il periodo. {@code menu} serve solo per MENU_TIPS.
     */
    public record Dataset(InsightPeriod period, List<ComandRow> comands, List<TopProduct> products,
                          List<ReservationRow> reservations, List<SoldOut> soldOut, List<MenuProduct> menu) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Highlight(String label, String value, String trend, String detail) {}

    /** Risultato: {@code noData} se nessuna comanda completata nel periodo (facts e highlights vuoti). */
    public record Result(boolean noData, Map<String, Object> facts, List<Highlight> highlights, String fallbackText) {
        static Result empty() { return new Result(true, Map.of(), List.of(), null); }
    }

    public static Result compute(Dataset data) {
        return switch (data.period().kind()) {
            case DAILY -> daily(data);
            case WEEKLY -> weekly(data);
            case MENU_TIPS -> menuTips(data);
        };
    }

    // ── DAILY ────────────────────────────────────────────────────────────────

    static Result daily(Dataset data) {
        LocalDate day = data.period().from();
        List<ComandRow> current = between(data.comands(), day, day);
        if (current.isEmpty()) return Result.empty();
        Totals t = Totals.of(current);

        // Confronto con lo stesso giorno della settimana nelle 4 settimane precedenti (solo i giorni con comande)
        List<Totals> previous = new ArrayList<>();
        for (int w = 1; w <= COMPARISON_WEEKS; w++) {
            LocalDate d = day.minusWeeks(w);
            List<ComandRow> rows = between(data.comands(), d, d);
            if (!rows.isEmpty()) previous.add(Totals.of(rows));
        }
        String reference = sameWeekdayReference(day.getDayOfWeek(), previous.size());

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tipo", "riepilogo del giorno");
        f.put("giorno", day.format(DAY_LONG));
        putTotals(f, t);
        putComparison(f, t, previous, reference);
        Optional<int[]> peak = peakHour(current);
        peak.ifPresent(p -> f.put("ora_di_punta", hourBand(p[0]) + " (" + p[1] + " comande)"));
        List<TopProduct> top = topSold(data.products(), 3);
        if (!top.isEmpty()) f.put("piu_venduti", productList(top));
        Reservations res = Reservations.of(data.reservations());
        if (res.any()) f.put("prenotazioni", res.facts());
        if (!data.soldOut().isEmpty()) f.put("esauriti", data.soldOut().stream().map(SoldOut::name).toList());

        List<Highlight> h = new ArrayList<>();
        h.add(trendHighlight("Incasso", euro(t.revenue), t.revenue, previous.isEmpty() ? null : avg(previous, true), reference));
        h.add(trendHighlight("Comande", String.valueOf(t.count), t.count, previous.isEmpty() ? null : avg(previous, false), reference));
        h.add(new Highlight("Scontrino medio", euroCents(t.avgTicket()), null, null));
        peak.ifPresent(p -> h.add(new Highlight("Ora di punta", hourBand(p[0]), null, p[1] + (p[1] == 1 ? " comanda" : " comande"))));
        if (res.any()) h.add(res.highlight());
        if (!top.isEmpty()) h.add(new Highlight("Più venduto", top.get(0).name(), null, portions(top.get(0).quantity())));

        // Testo di riserva
        StringBuilder s = new StringBuilder();
        s.append("Ieri, ").append(day.format(DAY_LONG).replace(" " + day.getYear(), ""))
                .append(", hai incassato ").append(euro(t.revenue)).append(" con ").append(comande(t.count))
                .append(" (scontrino medio ").append(euroCents(t.avgTicket())).append(").");
        appendComparisonSentence(s, t, previous, reference);
        peak.ifPresent(p -> s.append(" Il momento più intenso è stato tra le ").append(hourBand(p[0]).replace("–", " e le ")).append('.'));
        if (!top.isEmpty()) s.append(" Il piatto più richiesto è stato ").append(top.get(0).name())
                .append(" (").append(portions(top.get(0).quantity())).append(").");
        if (res.any()) s.append(' ').append(res.sentence());
        if (!data.soldOut().isEmpty()) s.append(" Sono andati esauriti: ")
                .append(String.join(", ", data.soldOut().stream().map(SoldOut::name).toList())).append('.');
        return new Result(false, f, cap(h), s.toString());
    }

    // ── WEEKLY ───────────────────────────────────────────────────────────────

    static Result weekly(Dataset data) {
        LocalDate from = data.period().from(), to = data.period().to();
        List<ComandRow> current = between(data.comands(), from, to);
        if (current.isEmpty()) return Result.empty();
        Totals t = Totals.of(current);

        List<Totals> previous = new ArrayList<>();
        for (int w = 1; w <= COMPARISON_WEEKS; w++) {
            List<ComandRow> rows = between(data.comands(), from.minusWeeks(w), to.minusWeeks(w));
            if (!rows.isEmpty()) previous.add(Totals.of(rows));
        }
        String reference = previous.size() == 1 ? "la settimana precedente"
                : "media delle " + previous.size() + " settimane precedenti";

        // Incasso per giorno
        Map<LocalDate, Totals> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<ComandRow> rows = between(current, d, d);
            if (!rows.isEmpty()) byDay.put(d, Totals.of(rows));
        }
        Map.Entry<LocalDate, Totals> best = byDay.entrySet().stream()
                .max(Comparator.comparingLong((Map.Entry<LocalDate, Totals> e) -> e.getValue().revenue)).orElseThrow();
        Map.Entry<LocalDate, Totals> worst = byDay.size() < 2 ? null : byDay.entrySet().stream()
                .min(Comparator.comparingLong((Map.Entry<LocalDate, Totals> e) -> e.getValue().revenue)).orElseThrow();

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tipo", "riepilogo della settimana");
        f.put("settimana", "da " + from.format(DAY_LONG) + " a " + to.format(DAY_LONG));
        putTotals(f, t);
        putComparison(f, t, previous, reference);
        f.put("giorni_con_comande", byDay.size());
        Map<String, String> perDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Totals dt = byDay.get(d);
            perDay.put(weekday(d.getDayOfWeek()), dt == null ? "chiuso o nessuna comanda" : euro(dt.revenue) + " (" + comande(dt.count) + ")");
        }
        f.put("incasso_per_giorno", perDay);
        f.put("giorno_migliore", weekday(best.getKey().getDayOfWeek()) + " " + best.getKey().format(DAY_MONTH) + ": " + euro(best.getValue().revenue));
        if (worst != null) f.put("giorno_piu_debole", weekday(worst.getKey().getDayOfWeek()) + " " + worst.getKey().format(DAY_MONTH) + ": " + euro(worst.getValue().revenue));
        List<TopProduct> top = topSold(data.products(), 5);
        if (!top.isEmpty()) f.put("piu_venduti", productList(top));
        Reservations res = Reservations.of(data.reservations());
        if (res.any()) f.put("prenotazioni", res.facts());
        if (!data.soldOut().isEmpty()) f.put("esauriti", soldOutList(data.soldOut(), 10));

        List<Highlight> h = new ArrayList<>();
        h.add(trendHighlight("Incasso", euro(t.revenue), t.revenue, previous.isEmpty() ? null : avg(previous, true), reference));
        h.add(trendHighlight("Comande", String.valueOf(t.count), t.count, previous.isEmpty() ? null : avg(previous, false), reference));
        h.add(new Highlight("Scontrino medio", euroCents(t.avgTicket()), null, null));
        h.add(new Highlight("Giorno migliore", capitalize(weekday(best.getKey().getDayOfWeek())), null, euro(best.getValue().revenue)));
        if (worst != null) h.add(new Highlight("Giorno più debole", capitalize(weekday(worst.getKey().getDayOfWeek())), null, euro(worst.getValue().revenue)));
        if (res.any()) h.add(res.highlight());

        StringBuilder s = new StringBuilder();
        s.append("Dal ").append(from.format(DAY_MONTH)).append(" al ").append(to.format(DAY_MONTH))
                .append(" hai incassato ").append(euro(t.revenue)).append(" con ").append(comande(t.count))
                .append(" (scontrino medio ").append(euroCents(t.avgTicket())).append(").");
        appendComparisonSentence(s, t, previous, reference);
        s.append(" Il giorno migliore è stato ").append(weekday(best.getKey().getDayOfWeek()))
                .append(" con ").append(euro(best.getValue().revenue));
        if (worst != null) s.append(", il più debole ").append(weekday(worst.getKey().getDayOfWeek()))
                .append(" con ").append(euro(worst.getValue().revenue));
        s.append('.');
        if (!top.isEmpty()) s.append(" I piatti più venduti: ").append(productText(top)).append('.');
        if (res.any()) s.append(' ').append(res.sentence());
        List<SoldOut> often = data.soldOut().stream().filter(x -> x.times() >= SOLD_OUT_MIN).toList();
        if (!often.isEmpty()) s.append(" Esauriti più volte: ").append(soldOutText(often, 5)).append('.');
        return new Result(false, f, cap(h), s.toString());
    }

    // ── MENU_TIPS ────────────────────────────────────────────────────────────

    /** Fasce orarie per l'analisi del menu: [inizio, fine) in ore. */
    static final String[][] BANDS = {{"pranzo (11-15)", "11", "15"}, {"pomeriggio (15-18)", "15", "18"},
            {"cena (18-23)", "18", "23"}, {"altri orari", "23", "11"}};

    static Result menuTips(Dataset data) {
        LocalDate from = data.period().from(), to = data.period().to();
        int days = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        List<ComandRow> current = between(data.comands(), from, to);
        if (current.isEmpty()) return Result.empty();
        Totals t = Totals.of(current);

        Map<Long, Long> sold = new HashMap<>();
        for (TopProduct p : data.products()) sold.merge(p.idProduct(), p.quantity(), Long::sum);
        List<TopProduct> top = topSold(data.products(), 5);
        List<MenuProduct> low = data.menu().stream()
                .filter(m -> sold.getOrDefault(m.idProduct(), 0L) <= LOW_SELLER_MAX)
                .sorted(Comparator.comparingLong((MenuProduct m) -> sold.getOrDefault(m.idProduct(), 0L))
                        .thenComparing(m -> m.name() == null ? "" : m.name()))
                .toList();
        List<SoldOut> often = data.soldOut().stream().filter(x -> x.times() >= SOLD_OUT_MIN).toList();

        // Media incasso per giorno della settimana (solo i giorni in cui il locale ha lavorato)
        Map<DayOfWeek, long[]> wd = new EnumMap<>(DayOfWeek.class);   // [incasso, giorni con comande]
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<ComandRow> rows = between(current, d, d);
            if (rows.isEmpty()) continue;
            long[] acc = wd.computeIfAbsent(d.getDayOfWeek(), k -> new long[2]);
            acc[0] += Totals.of(rows).revenue;
            acc[1]++;
        }
        Map<DayOfWeek, Long> wdAvg = new EnumMap<>(DayOfWeek.class);
        wd.forEach((k, v) -> wdAvg.put(k, v[0] / v[1]));
        DayOfWeek weakest = wdAvg.size() < 2 ? null
                : wdAvg.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        DayOfWeek strongest = wdAvg.isEmpty() ? null
                : wdAvg.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

        long takeaway = current.stream().filter(c -> "FROM_TAKEAWAY".equals(c.type()) || "FROM_HOME".equals(c.type())).count();
        int takeawayPct = pct(takeaway, t.count);

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("tipo", "consigli sul menu");
        f.put("periodo", "ultimi " + days + " giorni, da " + from.format(DAY_LONG) + " a " + to.format(DAY_LONG));
        f.put("giorni", days);
        putTotals(f, t);
        if (!top.isEmpty()) f.put("piu_venduti", productList(top));
        if (!low.isEmpty()) {
            f.put("poco_venduti_nel_menu", low.stream().limit(10).map(m -> Map.of("nome", String.valueOf(m.name()),
                    "porzioni_vendute", sold.getOrDefault(m.idProduct(), 0L))).toList());
            f.put("numero_piatti_poco_venduti", low.size());
            f.put("soglia_poco_venduti", "venduti al massimo " + LOW_SELLER_MAX + " volte in " + days + " giorni");
        }
        if (!often.isEmpty()) f.put("esauriti_spesso", soldOutList(often, 10));
        Map<String, String> perWeekday = new LinkedHashMap<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            perWeekday.put(weekday(d), wdAvg.containsKey(d) ? euro(wdAvg.get(d)) : "chiuso o nessuna comanda");
        }
        f.put("incasso_medio_per_giorno_della_settimana", perWeekday);
        if (weakest != null) f.put("giorno_piu_debole", weekday(weakest) + " (media " + euro(wdAvg.get(weakest)) + ")");
        if (strongest != null) f.put("giorno_piu_forte", weekday(strongest) + " (media " + euro(wdAvg.get(strongest)) + ")");
        Map<String, String> bands = new LinkedHashMap<>();
        for (String[] b : BANDS) {
            long n = current.stream().filter(c -> inBand(c.createdAt().getHour(), b)).count();
            bands.put(b[0], pct(n, t.count) + "% delle comande");
        }
        f.put("fasce_orarie", bands);
        f.put("quota_asporto_e_domicilio", takeawayPct + "% delle comande");

        List<Highlight> h = new ArrayList<>();
        if (!top.isEmpty()) h.add(new Highlight("Più venduto", top.get(0).name(), null, portions(top.get(0).quantity()) + " in " + days + " giorni"));
        if (!low.isEmpty()) h.add(new Highlight("Poco venduti", low.size() == 1 ? "1 piatto" : low.size() + " piatti", null,
                "venduti al massimo " + LOW_SELLER_MAX + " volte in " + days + " giorni"));
        if (!often.isEmpty()) h.add(new Highlight("Esauriti spesso", often.size() == 1 ? "1 piatto" : often.size() + " piatti", null,
                often.stream().limit(3).map(SoldOut::name).collect(Collectors.joining(", "))));
        if (weakest != null) h.add(new Highlight("Giorno più debole", capitalize(weekday(weakest)), null, "media " + euro(wdAvg.get(weakest))));
        h.add(new Highlight("Asporto e domicilio", takeawayPct + "%", null, "delle comande"));
        if (h.size() < 3) h.add(new Highlight("Comande", String.valueOf(t.count), null, "in " + days + " giorni"));
        if (h.size() < 3) h.add(new Highlight("Incasso", euro(t.revenue), null, "in " + days + " giorni"));

        List<String> tips = new ArrayList<>();
        if (!top.isEmpty()) tips.add("Valorizza " + top.get(0).name() + ": è il piatto più venduto ("
                + portions(top.get(0).quantity()) + " in " + days + " giorni). Mettilo in evidenza nel menu.");
        if (!low.isEmpty()) tips.add("Valuta se rinnovare, promuovere o togliere i piatti poco richiesti: "
                + low.stream().limit(5).map(MenuProduct::name).collect(Collectors.joining(", "))
                + (low.size() > 5 ? " e altri " + (low.size() - 5) : "") + ".");
        if (!often.isEmpty()) tips.add("Controlla le scorte di " + soldOutText(often, 3) + ": sono andati esauriti più volte.");
        if (weakest != null) tips.add((weakest == DayOfWeek.SUNDAY ? "La " : "Il ") + weekday(weakest) + " è il giorno più debole (media " + euro(wdAvg.get(weakest))
                + "): prova una proposta dedicata per quel giorno.");
        tips.add("Asporto e domicilio valgono il " + takeawayPct + "% delle comande.");
        String text = tips.stream().limit(5).map(x -> "- " + x).collect(Collectors.joining("\n"));
        return new Result(false, f, cap(h), text);
    }

    static boolean inBand(int hour, String[] band) {
        int from = Integer.parseInt(band[1]), to = Integer.parseInt(band[2]);
        return from < to ? hour >= from && hour < to : hour >= from || hour < to;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Totali di un insieme di comande. */
    record Totals(long revenue, long count) {
        static Totals of(List<ComandRow> rows) {
            long r = 0;
            for (ComandRow c : rows) r += c.totalCents();
            return new Totals(r, rows.size());
        }
        long avgTicket() { return count == 0 ? 0 : Math.round((double) revenue / count); }
    }

    /** Prenotazioni del periodo: totali (escluse le cancellate), coperti attesi, no-show e cancellate. */
    record Reservations(int total, int guests, int noShow, int cancelled) {
        static Reservations of(List<ReservationRow> rows) {
            int total = 0, guests = 0, noShow = 0, cancelled = 0;
            for (ReservationRow r : rows) {
                String st = r.status() == null ? "" : r.status();
                switch (st) {
                    case "CANCELLED" -> cancelled++;
                    case "NO_SHOW" -> { noShow++; total++; }
                    default -> { total++; guests += Math.max(0, r.partySize()); }
                }
            }
            return new Reservations(total, guests, noShow, cancelled);
        }
        boolean any() { return total + cancelled > 0; }
        Map<String, Object> facts() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totali", total);
            m.put("coperti", guests);
            m.put("no_show", noShow);
            m.put("cancellate", cancelled);
            return m;
        }
        Highlight highlight() {
            List<String> d = new ArrayList<>();
            d.add(guests == 1 ? "1 coperto" : guests + " coperti");
            if (noShow > 0) d.add(noShow + " no-show");
            if (cancelled > 0) d.add(cancelled == 1 ? "1 cancellata" : cancelled + " cancellate");
            return new Highlight("Prenotazioni", String.valueOf(total), null, String.join(" · ", d));
        }
        String sentence() {
            StringBuilder s = new StringBuilder("Prenotazioni: ").append(total)
                    .append(" per ").append(guests == 1 ? "1 coperto" : guests + " coperti");
            if (noShow > 0) s.append(", ").append(noShow).append(" no-show");
            if (cancelled > 0) s.append(", ").append(cancelled).append(cancelled == 1 ? " cancellata" : " cancellate");
            return s.append('.').toString();
        }
    }

    static List<ComandRow> between(List<ComandRow> rows, LocalDate from, LocalDate to) {
        List<ComandRow> out = new ArrayList<>();
        for (ComandRow c : rows) {
            LocalDate d = c.createdAt().toLocalDate();
            if (!d.isBefore(from) && !d.isAfter(to)) out.add(c);
        }
        return out;
    }

    private static void putTotals(Map<String, Object> f, Totals t) {
        f.put("incasso", euro(t.revenue));
        f.put("nota_incasso", "totale lordo delle comande completate, prima degli sconti");
        f.put("comande", t.count);
        f.put("scontrino_medio", euroCents(t.avgTicket()));
    }

    private static void putComparison(Map<String, Object> f, Totals t, List<Totals> previous, String reference) {
        if (previous.isEmpty()) {
            f.put("confronto", "nessun dato dei periodi precedenti");
            return;
        }
        long avgRevenue = avg(previous, true), avgCount = avg(previous, false);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("riferimento", reference);
        c.put("incasso_di_riferimento", euro(avgRevenue));
        if (avgRevenue > 0) c.put("variazione_incasso", signedPct(change(t.revenue, avgRevenue)));
        c.put("comande_di_riferimento", avgCount);
        if (avgCount > 0) c.put("variazione_comande", signedPct(change(t.count, avgCount)));
        f.put("confronto", c);
    }

    private static void appendComparisonSentence(StringBuilder s, Totals t, List<Totals> previous, String reference) {
        if (previous.isEmpty()) return;
        long avgRevenue = avg(previous, true);
        if (avgRevenue <= 0) return;
        int p = change(t.revenue, avgRevenue);
        String ref = reference.startsWith("media") ? "alla " + reference : "a " + reference;
        if (Math.abs(p) < FLAT_THRESHOLD_PCT) s.append(" L'incasso è in linea rispetto ").append(ref).append(" (").append(euro(avgRevenue)).append(").");
        else s.append(" È il ").append(Math.abs(p)).append("% in ").append(p > 0 ? "più" : "meno")
                .append(" rispetto ").append(ref).append(" (").append(euro(avgRevenue)).append(").");
    }

    /** Media arrotondata degli incassi (revenue=true) o del numero di comande. */
    static long avg(List<Totals> list, boolean revenue) {
        if (list.isEmpty()) return 0;
        long sum = 0;
        for (Totals t : list) sum += revenue ? t.revenue : t.count;
        return Math.round((double) sum / list.size());
    }

    /** Variazione percentuale arrotondata di {@code current} rispetto a {@code reference} (> 0). */
    static int change(long current, long reference) {
        return (int) Math.round((current - reference) * 100.0 / reference);
    }

    static Highlight trendHighlight(String label, String value, long current, Long reference, String referenceText) {
        if (reference == null || reference <= 0) return new Highlight(label, value, null, null);
        int p = change(current, reference);
        String trend = p >= FLAT_THRESHOLD_PCT ? "up" : p <= -FLAT_THRESHOLD_PCT ? "down" : "flat";
        return new Highlight(label, value, trend, signedPct(p) + " vs " + referenceText);
    }

    /** "media dei 4 venerdì precedenti", "media delle 3 domeniche precedenti", "il sabato precedente". */
    static String sameWeekdayReference(DayOfWeek day, int n) {
        String name = weekday(day);
        if (n == 1) return (day == DayOfWeek.SUNDAY ? "la " : "il ") + name + " precedente";
        String plural = switch (day) {
            case SATURDAY -> "sabati";
            case SUNDAY -> "domeniche";
            default -> name;
        };
        return "media " + (day == DayOfWeek.SUNDAY ? "delle " : "dei ") + n + " " + plural + " precedenti";
    }

    /** Ora con più comande (a parità, la prima): [ora, numero comande]. */
    static Optional<int[]> peakHour(List<ComandRow> rows) {
        int[] counts = new int[24];
        for (ComandRow c : rows) counts[c.createdAt().getHour()]++;
        int best = -1;
        for (int h = 0; h < 24; h++) if (counts[h] > 0 && (best < 0 || counts[h] > counts[best])) best = h;
        return best < 0 ? Optional.empty() : Optional.of(new int[]{best, counts[best]});
    }

    static String hourBand(int hour) {
        return String.format("%02d:00–%02d:00", hour, (hour + 1) % 24);
    }

    static List<TopProduct> topSold(List<TopProduct> products, int n) {
        return products.stream().filter(p -> p.quantity() > 0)
                .sorted(Comparator.comparingLong(TopProduct::quantity).reversed()
                        .thenComparing(Comparator.comparingLong(TopProduct::revenueCents).reversed()))
                .limit(n).toList();
    }

    private static List<Map<String, Object>> productList(List<TopProduct> top) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TopProduct p : top) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", p.name());
            m.put("porzioni", p.quantity());
            m.put("incasso", euro(p.revenueCents()));
            out.add(m);
        }
        return out;
    }

    private static String productText(List<TopProduct> top) {
        return top.stream().map(p -> p.name() + " (" + p.quantity() + ")").collect(Collectors.joining(", "));
    }

    private static List<Map<String, Object>> soldOutList(List<SoldOut> list, int max) {
        return list.stream().limit(max).map(x -> Map.<String, Object>of("nome", x.name(), "volte", x.times())).toList();
    }

    private static String soldOutText(List<SoldOut> list, int max) {
        return list.stream().limit(max).map(x -> x.name() + " (" + x.times() + " volte)").collect(Collectors.joining(", "));
    }

    private static String portions(long q) {
        return q == 1 ? "1 porzione" : q + " porzioni";
    }

    private static String comande(long n) {
        return n == 1 ? "1 comanda" : n + " comande";
    }

    static String weekday(DayOfWeek d) {
        return d.getDisplayName(TextStyle.FULL, IT);
    }

    static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static List<Highlight> cap(List<Highlight> h) {
        return h.size() > 6 ? List.copyOf(h.subList(0, 6)) : List.copyOf(h);
    }

    static int pct(long part, long total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    // ── Formattazione italiana ───────────────────────────────────────────────

    private static DecimalFormat format(String pattern) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ITALY);
        sym.setGroupingSeparator('.');
        sym.setDecimalSeparator(',');
        return new DecimalFormat(pattern, sym);
    }

    /** Importo: da 100 € in su senza decimali ("1.240 €"), sotto con i centesimi ("29,50 €"). */
    static String euro(long cents) {
        if (Math.abs(cents) >= 10_000) return format("#,##0").format(Math.round(cents / 100.0)) + " €";
        return euroCents(cents);
    }

    /** Importo sempre con i centesimi ("1.240,50 €"). */
    static String euroCents(long cents) {
        return format("#,##0.00").format(cents / 100.0) + " €";
    }

    /** "+18%", "-7%", "0%". */
    static String signedPct(int p) {
        return (p > 0 ? "+" : "") + p + "%";
    }
}
