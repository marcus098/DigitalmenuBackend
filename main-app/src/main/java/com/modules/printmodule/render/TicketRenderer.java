package com.modules.printmodule.render;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderer puro (nessuna dipendenza Spring) della comanda in righe di testo.
 *
 * Ogni riga ha un prefisso di stile di 2 caratteri, interpretato dagli encoder:
 *   "N|" normale, "B|" grassetto, "C|" grassetto centrato, "H|" grande (doppia altezza/larghezza) centrato.
 * Le righe "H|" sono già spezzate a width/2 caratteri.
 *
 * Il testo è solo ASCII stampabile: gli accenti vengono traslitterati (à → a', é → e', € → EUR) così
 * lo scontrino è identico su qualsiasi stampante ESC/POS economica, senza dipendere dalla code page.
 */
public final class TicketRenderer {

    public static final String NORMAL = "N|";
    public static final String BOLD = "B|";
    public static final String CENTER = "C|";
    public static final String HUGE = "H|";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_IN_TEXT = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private TicketRenderer() {}

    // ── Comanda ──────────────────────────────────────────────────────────────

    public static List<String> render(TicketData d, int width) {
        List<String> out = new ArrayList<>();
        int hugeWidth = Math.max(8, width / 2);

        if (!sanitize(d.restaurantName()).isEmpty()) add(out, CENTER, wrap(sanitize(d.restaurantName()), width, 0, 0));
        out.add(NORMAL + "=".repeat(width));

        String customer = sanitize(d.customerName());
        String phone = sanitize(d.phone());
        String pickup = formatPickup(d.pickupTime());
        switch (d.orderKind() == null ? TicketData.OrderKind.TABLE : d.orderKind()) {
            case TABLE -> {
                String table = sanitize(d.tableName());
                add(out, HUGE, wrap("TAVOLO " + (table.isEmpty() ? "?" : table.toUpperCase()), hugeWidth, 0, 0));
            }
            case TAKE_AWAY -> {
                add(out, HUGE, wrap("ASPORTO", hugeWidth, 0, 0));
                if (!pickup.isEmpty()) add(out, HUGE, wrap("RITIRO " + pickup, hugeWidth, 0, 0));
                if (!customer.isEmpty()) add(out, BOLD, wrap(customer, width, 0, 0));
                if (!phone.isEmpty()) add(out, NORMAL, wrap("Tel: " + phone, width, 0, 5));
            }
            case HOME -> {
                add(out, HUGE, wrap("DOMICILIO", hugeWidth, 0, 0));
                if (!pickup.isEmpty()) add(out, CENTER, wrap("Consegna " + pickup, width, 0, 0));
                if (!customer.isEmpty()) add(out, BOLD, wrap(customer, width, 0, 0));
                String address = sanitize(d.address());
                if (!address.isEmpty()) add(out, NORMAL, wrap(address, width, 0, 0));
                if (!phone.isEmpty()) add(out, NORMAL, wrap("Tel: " + phone, width, 0, 5));
            }
        }

        String banner = sanitize(d.banner());
        if (!banner.isEmpty()) add(out, CENTER, wrap("*** " + banner.toUpperCase() + " ***", width, 0, 0));

        String when = d.createdAt() != null ? d.createdAt().format(DATE_TIME) : "";
        String id = sanitize(d.shortId());
        out.add(NORMAL + leftRight(when, id.isEmpty() ? "" : "#" + id, width));
        out.add(NORMAL + "-".repeat(width));

        Map<String, List<TicketData.Item>> byCategory = new LinkedHashMap<>();
        for (TicketData.Item item : d.items()) {
            String cat = sanitize(item.category());
            byCategory.computeIfAbsent(cat.isEmpty() ? "ALTRO" : cat.toUpperCase(), k -> new ArrayList<>()).add(item);
        }

        int totalQty = 0;
        boolean first = true;
        for (Map.Entry<String, List<TicketData.Item>> group : byCategory.entrySet()) {
            if (!first) out.add(NORMAL);
            first = false;
            add(out, BOLD, wrap("[ " + group.getKey() + " ]", width, 0, 2));
            for (TicketData.Item item : group.getValue()) {
                totalQty += Math.max(0, item.quantity());
                String qty = item.quantity() + "x ";
                String name = sanitize(item.name());
                String option = sanitize(item.option());
                if (!option.isEmpty()) name += " (" + option + ")";
                add(out, BOLD, wrap(qty + name, width, 0, qty.length()));
                if (item.minus() != null) for (String m : item.minus()) {
                    String s = sanitize(m);
                    if (!s.isEmpty()) add(out, NORMAL, wrap("- senza " + s, width, 3, 5));
                }
                if (item.plus() != null) for (String p : item.plus()) {
                    String s = sanitize(p);
                    if (!s.isEmpty()) add(out, NORMAL, wrap("+ " + s, width, 3, 5));
                }
                String note = sanitize(item.note());
                if (!note.isEmpty()) add(out, NORMAL, wrap("Note: " + note, width, 3, 5));
            }
        }

        out.add(NORMAL + "-".repeat(width));
        out.add(NORMAL + leftRight("Articoli: " + totalQty, id.isEmpty() ? "" : "#" + id, width));
        return out;
    }

    // ── Test ─────────────────────────────────────────────────────────────────

    public static List<String> renderTest(String restaurantName, String printerName, int width, LocalDateTime now) {
        List<String> out = new ArrayList<>();
        if (!sanitize(restaurantName).isEmpty()) add(out, CENTER, wrap(sanitize(restaurantName), width, 0, 0));
        out.add(NORMAL + "=".repeat(width));
        out.add(HUGE + "TEST");
        add(out, NORMAL, wrap("Stampante: " + sanitize(printerName), width, 0, 2));
        out.add(NORMAL + "Larghezza: " + width + " caratteri");
        StringBuilder ruler = new StringBuilder();
        for (int i = 1; i <= width; i++) ruler.append(i % 10);
        out.add(NORMAL + ruler);
        add(out, NORMAL, wrap(sanitize("Accenti: città perché più però così - 10€"), width, 0, 2));
        out.add(BOLD + "Grassetto");
        out.add(NORMAL + (now != null ? now.format(DATE_TIME) : ""));
        out.add(NORMAL + "-".repeat(width));
        return out;
    }

    // ── Output testo semplice (CloudPRNT text/plain, anteprima dashboard) ────

    public static String toPlainText(List<String> lines, int width) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String style = styleOf(line);
            String text = textOf(line);
            if (CENTER.equals(style) || HUGE.equals(style)) {
                int pad = Math.max(0, (width - text.length()) / 2);
                sb.append(" ".repeat(pad));
            }
            sb.append(text).append('\n');
        }
        return sb.toString();
    }

    public static String styleOf(String line) {
        return line != null && line.length() >= 2 && line.charAt(1) == '|' ? line.substring(0, 2) : NORMAL;
    }

    public static String textOf(String line) {
        if (line == null) return "";
        return line.length() >= 2 && line.charAt(1) == '|' ? line.substring(2) : line;
    }

    // ── Helpers (package-visible per i test) ─────────────────────────────────

    /** Traslittera in ASCII stampabile (0x20-0x7E): vocali accentate → vocale + apostrofo, resto senza diacritici. */
    public static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c <= 0x7E) { sb.append(c); continue; }
            switch (c) {
                case '\t', '\r', '\n' -> sb.append(' ');
                case '€' -> sb.append("EUR");
                case 'ß' -> sb.append("ss");
                case 'æ' -> sb.append("ae");
                case 'Æ' -> sb.append("AE");
                case 'œ' -> sb.append("oe");
                case 'Œ' -> sb.append("OE");
                case 'ø' -> sb.append('o');
                case 'Ø' -> sb.append('O');
                case '‘', '’', '´', '`' -> sb.append('\'');
                case '“', '”', '«', '»' -> sb.append('"');
                case '–', '—' -> sb.append('-');
                case '…' -> sb.append("...");
                case '°', 'º' -> sb.append("o");
                case ' ' -> sb.append(' ');
                default -> {
                    String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                    char base = decomposed.charAt(0);
                    if (base < 0x20 || base > 0x7E) break; // carattere non traslitterabile: scartato
                    sb.append(base);
                    boolean graveOrAcute = decomposed.indexOf('̀') > 0 || decomposed.indexOf('́') > 0;
                    if (graveOrAcute && "aeiouAEIOU".indexOf(base) >= 0) sb.append('\'');
                }
            }
        }
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    /** Word-wrap con indentazione della prima riga e delle successive; spezza le parole più lunghe della riga. */
    public static List<String> wrap(String text, int width, int firstIndent, int restIndent) {
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split(" +");
        StringBuilder cur = new StringBuilder(" ".repeat(firstIndent));
        int indent = firstIndent;
        boolean empty = true;
        for (String w : words) {
            if (w.isEmpty()) continue;
            while (true) {
                int avail = width - cur.length() - (empty ? 0 : 1);
                if (w.length() <= avail) {
                    if (!empty) cur.append(' ');
                    cur.append(w);
                    empty = false;
                    break;
                }
                if (!empty) { // va a capo e riprova
                    lines.add(cur.toString());
                    indent = restIndent;
                    cur = new StringBuilder(" ".repeat(indent));
                    empty = true;
                    continue;
                }
                int room = Math.max(1, width - cur.length()); // parola più lunga della riga: spezzala
                cur.append(w, 0, Math.min(room, w.length()));
                w = w.substring(Math.min(room, w.length()));
                lines.add(cur.toString());
                indent = restIndent;
                cur = new StringBuilder(" ".repeat(indent));
                if (w.isEmpty()) break;
            }
        }
        if (!empty || lines.isEmpty()) lines.add(cur.toString().stripTrailing());
        return lines;
    }

    static String leftRight(String left, String right, int width) {
        int space = width - left.length() - right.length();
        if (space < 1) return (left + " " + right).length() <= width ? left + " " + right : left.substring(0, Math.min(left.length(), width));
        return left + " ".repeat(space) + right;
    }

    static String formatPickup(String raw) {
        String s = sanitize(raw);
        if (s.isEmpty()) return "";
        try {
            return LocalDateTime.parse(s).format(HHMM);
        } catch (Exception ignored) { }
        Matcher m = TIME_IN_TEXT.matcher(s);
        if (m.find()) return String.format("%02d:%s", Integer.parseInt(m.group(1)), m.group(2));
        return s;
    }

    public static String shortId(String comandId) {
        if (comandId == null || comandId.isEmpty()) return "";
        String base = comandId.contains("_") ? comandId.substring(0, comandId.indexOf('_')) : comandId;
        base = base.replaceAll("[^A-Za-z0-9]", "");
        if (base.isEmpty()) return "";
        String s = base.length() == 24 ? base.substring(18) : base.substring(0, Math.min(6, base.length()));
        return s.toUpperCase();
    }

    private static void add(List<String> out, String style, List<String> lines) {
        for (String l : lines) out.add(style + l);
    }
}
