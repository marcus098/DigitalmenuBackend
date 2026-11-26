package com.modules.printmodule.render;

import com.modules.common.model.ComandItemChange;
import com.modules.common.model.enums.ComandItemChangeType;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderer puro (nessuna dipendenza Spring) della comanda in righe di testo.
 *
 * Ogni riga ha un prefisso di stile di 2 caratteri, interpretato dagli encoder:
 *   "N|" normale, "B|" grassetto, "C|" grassetto centrato, "H|" grande (doppia altezza/larghezza) centrato,
 *   "I|" in evidenza (grassetto in negativo: bianco su nero), usato per le varianti highlight (es. senza glutine).
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
    public static final String INVERTED = "I|";
    /** Prefisso delle varianti in evidenza: "/!\" è il segnale di pericolo in ASCII (il simbolo ⚠ non è stampabile). */
    public static final String HIGHLIGHT_MARK = "/!\\ ";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_IN_TEXT = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private TicketRenderer() {}

    // ── Comanda ──────────────────────────────────────────────────────────────

    public static List<String> render(TicketData d, int width) {
        List<String> out = new ArrayList<>();
        header(out, d, width);

        String when = d.createdAt() != null ? d.createdAt().format(DATE_TIME) : "";
        String id = sanitize(d.shortId());
        out.add(NORMAL + leftRight(when, id.isEmpty() ? "" : "#" + id, width));
        out.add(NORMAL + "-".repeat(width));

        Map<String, List<TicketData.Item>> byCategory = new LinkedHashMap<>();
        for (TicketData.Item item : d.items()) {
            String cat = sanitize(item.category());
            byCategory.computeIfAbsent(cat.isEmpty() ? "ALTRO" : cat.toUpperCase(), k -> new ArrayList<>()).add(item);
        }
        renderItems(out, byCategory, id, width);
        return out;
    }

    // ── Variazione ordine ────────────────────────────────────────────────────

    /**
     * Scontrino "VARIAZIONE ORDINE": stessa intestazione della comanda (tavolo / asporto / domicilio, banner), chi ha
     * modificato e quando, poi SOLO le differenze (già filtrate per le categorie della stampante):
     * "- 2x Margherita" (tolto, in negativo), "+ 1x Coca" (aggiunto), "~ Diavola: 1x -> 3x", "~ 1x Margherita" con
     * "prima:/ora:" per formato/varianti/ingredienti e note.
     */
    public static List<String> renderVariation(TicketData d, List<ComandItemChange> changes, String editedBy,
                                               LocalDateTime editedAt, int width) {
        List<String> out = new ArrayList<>();
        header(out, d, width);
        String who = sanitize(editedBy);
        String when = editedAt != null ? editedAt.format(HHMM) : "";
        add(out, BOLD, wrap("Modificato" + (who.isEmpty() ? "" : " da " + who) + (when.isEmpty() ? "" : " alle " + when),
                width, 0, 2));
        String id = sanitize(d.shortId());
        String created = d.createdAt() != null ? "Ordine del " + d.createdAt().format(DATE_TIME) : "";
        out.add(NORMAL + leftRight(created, id.isEmpty() ? "" : "#" + id, width));
        out.add(NORMAL + "-".repeat(width));

        for (ComandItemChange c : changes) {
            if (c == null || c.getType() == null) continue;
            String name = sanitize(c.getProductName());
            int oldQty = c.getOldQty() == null ? 0 : c.getOldQty();
            int newQty = c.getNewQty() == null ? 0 : c.getNewQty();
            switch (c.getType()) {
                case REMOVED -> {
                    add(out, INVERTED, wrap("- " + oldQty + "x " + name, width, 0, 4));
                    detail(out, c.getOldDescription(), width);
                    note(out, "Note: ", c.getOldNote(), width);
                }
                case ADDED -> {
                    add(out, BOLD, wrap("+ " + newQty + "x " + name, width, 0, 4));
                    detail(out, c.getNewDescription(), width);
                    note(out, "Note: ", c.getNewNote(), width);
                }
                case QTY_CHANGED -> {
                    add(out, BOLD, wrap("~ " + name + ": " + oldQty + "x -> " + newQty + "x", width, 0, 4));
                    detail(out, c.getNewDescription(), width);
                    note(out, "Note: ", c.getNewNote(), width);
                }
                case VARIANT_CHANGED, NOTE_CHANGED -> {
                    String qty = oldQty == newQty ? newQty + "x" : oldQty + "x -> " + newQty + "x";
                    add(out, BOLD, wrap("~ " + qty + " " + name, width, 0, 4));
                    if (c.getType() == ComandItemChangeType.VARIANT_CHANGED) {
                        add(out, NORMAL, wrap("prima: " + orNone(c.getOldDescription()), width, 3, 10));
                        add(out, BOLD, wrap("ora: " + orNone(c.getNewDescription()), width, 3, 8));
                    } else {
                        detail(out, c.getNewDescription(), width);
                    }
                    if (!Objects.equals(c.getOldNote(), c.getNewNote())) {
                        add(out, NORMAL, wrap("Note prima: " + orNone(c.getOldNote()), width, 3, 5));
                        add(out, BOLD, wrap("Note ora: " + orNone(c.getNewNote()), width, 3, 5));
                    } else {
                        note(out, "Note: ", c.getNewNote(), width);
                    }
                }
            }
        }
        out.add(NORMAL + "-".repeat(width));
        add(out, CENTER, wrap("*** VARIAZIONE ORDINE ***", width, 0, 0));
        return out;
    }

    private static void detail(List<String> out, String description, int width) {
        String s = sanitize(description);
        if (!s.isEmpty()) add(out, NORMAL, wrap(s, width, 4, 4));
    }

    private static void note(List<String> out, String label, String note, int width) {
        String s = sanitize(note);
        if (!s.isEmpty()) add(out, NORMAL, wrap(label + s, width, 4, 6));
    }

    private static String orNone(String s) {
        String v = sanitize(s);
        return v.isEmpty() ? "(nessuna)" : v;
    }

    /** Intestazione comune: locale, tavolo/asporto/domicilio (con il blocco consegna) e banner. */
    private static void header(List<String> out, TicketData d, int width) {
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
                TicketData.Delivery del = d.delivery();
                if (del == null) {
                    if (!pickup.isEmpty()) add(out, CENTER, wrap("Consegna " + pickup, width, 0, 0));
                    if (!customer.isEmpty()) add(out, BOLD, wrap(customer, width, 0, 0));
                    String address = sanitize(d.address());
                    if (!address.isEmpty()) add(out, NORMAL, wrap(address, width, 0, 0));
                    if (!phone.isEmpty()) add(out, NORMAL, wrap("Tel: " + phone, width, 0, 5));
                } else {
                    renderDelivery(out, d, del, customer, phone, pickup, width, hugeWidth);
                }
            }
        }

        String banner = sanitize(d.banner());
        if (!banner.isEmpty()) add(out, CENTER, wrap("*** " + banner.toUpperCase() + " ***", width, 0, 0));
    }

    private static void renderItems(List<String> out, Map<String, List<TicketData.Item>> byCategory, String id, int width) {

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
                if (item.variants() != null) for (TicketData.Variant v : item.variants()) {
                    if (v == null) continue;
                    String choice = sanitize(v.choice());
                    if (choice.isEmpty()) continue;
                    if (v.highlight()) {
                        // In evidenza: riga a sé, maiuscolo, in negativo (es. "/!\ SENZA GLUTINE")
                        add(out, INVERTED, wrap(HIGHLIGHT_MARK + choice.toUpperCase(), width, 3, 7));
                    } else {
                        String groupName = sanitize(v.group());
                        add(out, NORMAL, wrap("> " + (groupName.isEmpty() ? "" : groupName + ": ") + choice, width, 3, 5));
                    }
                }
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
    }

    /**
     * Blocco DOMICILIO: orario grande, cliente, indirizzo, citofono, piano, note, telefono e pagamento
     * ("PAGATO ONLINE" oppure "DA INCASSARE EUR 23,50 CONTANTI|POS", in negativo).
     */
    private static void renderDelivery(List<String> out, TicketData d, TicketData.Delivery del, String customer,
                                       String phone, String pickup, int width, int hugeWidth) {
        if (!pickup.isEmpty()) add(out, HUGE, wrap("ORE " + pickup, hugeWidth, 0, 0));
        if (!customer.isEmpty()) add(out, BOLD, wrap(customer, width, 0, 0));
        String address = sanitize(d.address());
        if (!address.isEmpty()) add(out, BOLD, wrap(address, width, 0, 2));
        String intercom = sanitize(del.intercom());
        if (!intercom.isEmpty()) add(out, NORMAL, wrap("Citofono: " + intercom, width, 0, 10));
        String floor = sanitize(del.floor());
        if (!floor.isEmpty()) add(out, NORMAL, wrap("Piano: " + floor, width, 0, 7));
        String notes = sanitize(del.notes());
        if (!notes.isEmpty()) add(out, NORMAL, wrap("Note consegna: " + notes, width, 0, 2));
        if (!phone.isEmpty()) add(out, NORMAL, wrap("Tel: " + phone, width, 0, 5));
        if (del.distanceKm() != null) {
            out.add(NORMAL + String.format(java.util.Locale.ITALY, "Distanza: %.1f km", del.distanceKm()));
        }
        if (del.paidOnline()) {
            add(out, INVERTED, wrap("PAGATO ONLINE", width, 0, 0));
        } else {
            String method = "CARD".equals(del.method()) ? "POS" : "CONTANTI";
            add(out, INVERTED, wrap("DA INCASSARE " + euro(del.toCollectCents()) + " " + method, width, 0, 2));
        }
    }

    /** "EUR 23,50" (ASCII: il simbolo dell'euro non è stampabile su tutte le stampanti). */
    public static String euro(long cents) {
        return String.format(java.util.Locale.ITALY, "EUR %d,%02d", cents / 100, Math.abs(cents % 100));
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
