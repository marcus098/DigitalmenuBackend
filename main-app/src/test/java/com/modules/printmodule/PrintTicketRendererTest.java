package com.modules.printmodule;

import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintTicketRendererTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 9, 20, 15);

    private TicketData table() {
        return new TicketData("Pizzeria Da Nicolò", TicketData.OrderKind.TABLE, "5", null, null, null, null, AT,
                "A1B2C3", null, List.of(
                new TicketData.Item("Pizze", 2, "Margherita", null, List.of("cipolla"), List.of("bufala"), "ben cotta"),
                new TicketData.Item("Bevande", 1, "Coca Cola", "33cl", List.of(), List.of(), null),
                new TicketData.Item("Pizze", 1, "Diavola piccante extra lunga con nome davvero lunghissimo", null, List.of(), List.of(), null)));
    }

    @Test
    void tableTicketHasHeaderGroupsAndModifiers() {
        List<String> lines = TicketRenderer.render(table(), 48);
        List<String> text = lines.stream().map(TicketRenderer::textOf).toList();

        assertTrue(lines.contains("C|Pizzeria Da Nicolo'"));
        assertTrue(lines.contains("H|TAVOLO 5"));
        assertTrue(lines.contains("B|[ PIZZE ]"));
        assertTrue(lines.contains("B|2x Margherita"));
        assertTrue(lines.contains("N|   - senza cipolla"));
        assertTrue(lines.contains("N|   + bufala"));
        assertTrue(lines.contains("N|   Note: ben cotta"));
        assertTrue(lines.contains("B|1x Coca Cola (33cl)"));
        assertTrue(text.stream().anyMatch(l -> l.startsWith("09/10/2026 20:15") && l.endsWith("#A1B2C3")));
        assertTrue(text.stream().anyMatch(l -> l.startsWith("Articoli: 4")));
        // categorie raggruppate nell'ordine di prima apparizione: tutte le pizze prima delle bevande
        int diavola = indexOfStartingWith(text, "1x Diavola");
        int bevande = text.indexOf("[ BEVANDE ]");
        assertTrue(diavola > 0 && diavola < bevande);
    }

    @Test
    void respectsWidthOn58And80mm() {
        for (int width : new int[]{32, 48}) {
            for (String line : TicketRenderer.render(table(), width)) {
                int max = line.startsWith("H|") ? width / 2 : width;
                assertTrue(TicketRenderer.textOf(line).length() <= max, "riga troppo lunga a " + width + ": " + line);
            }
        }
        // wrap con indentazione sospesa sotto la quantità
        List<String> l32 = TicketRenderer.render(table(), 32);
        int i = indexOfStartingWith(l32.stream().map(TicketRenderer::textOf).toList(), "1x Diavola");
        assertTrue(TicketRenderer.textOf(l32.get(i + 1)).startsWith("   "));
    }

    @Test
    void takeawayAndHomeHeaders() {
        TicketData ta = new TicketData("X", TicketData.OrderKind.TAKE_AWAY, null, "Mario Rossi", "333 123", null,
                "2026-10-09T19:30:00", AT, "ABC", null, List.of(new TicketData.Item(null, 1, "Pizza", null, null, null, null)));
        List<String> lines = TicketRenderer.render(ta, 32);
        assertTrue(lines.contains("H|ASPORTO"));
        assertTrue(lines.contains("H|RITIRO 19:30"));
        assertTrue(lines.contains("B|Mario Rossi"));
        assertTrue(lines.contains("B|[ ALTRO ]"));

        TicketData home = new TicketData("X", TicketData.OrderKind.HOME, null, "Anna", null, "Via Roma 1", "20:00", AT,
                "ABC", "Ristampa", List.of(new TicketData.Item("Pizze", 1, "Pizza", null, null, null, null)));
        List<String> h = TicketRenderer.render(home, 48);
        assertTrue(h.contains("H|DOMICILIO"));
        assertTrue(h.contains("N|Via Roma 1"));
        assertTrue(h.contains("C|*** RISTAMPA ***"));
    }

    @Test
    void sanitizeTransliteratesAndStripsControlChars() {
        assertEquals("citta' perche' piu' E' EUR 10", TicketRenderer.sanitize("città perché più È € 10"));
        assertEquals("a b", TicketRenderer.sanitize("a\u0007\n\tb"));
        assertEquals("Muller ...", TicketRenderer.sanitize("Müller …"));
        assertEquals("", TicketRenderer.sanitize("🍕"));
        assertEquals("", TicketRenderer.sanitize(null));
    }

    @Test
    void wrapSplitsLongWords() {
        List<String> w = TicketRenderer.wrap("x ABCDEFGHIJKLMNOPQRSTUVWXYZ", 10, 0, 2);
        w.forEach(l -> assertTrue(l.length() <= 10, l));
        assertEquals("x", w.get(0));
    }

    @Test
    void shortIdFormats() {
        assertEquals("3F2A9C", TicketRenderer.shortId("3f2a9c11-2222-3333-4444-555566667777_1728490000000"));
        assertEquals("ABCDEF", TicketRenderer.shortId("65f1a2b3c4d5e6f7a8abcdef"));
    }

    @Test
    void plainTextCentersHeaders() {
        String txt = TicketRenderer.toPlainText(List.of("H|TEST", "N|abc"), 10);
        assertEquals("   TEST\nabc\n", txt);
    }

    @Test
    void escPosAndStarPrntFraming() {
        List<String> lines = TicketRenderer.render(table(), 48);
        byte[] esc = PrinterEncoder.escPos(lines, 2);
        assertArrayEquals(new byte[]{0x1B, '@'}, Arrays.copyOfRange(esc, 0, 2));
        assertArrayEquals(new byte[]{0x1D, 'V', 66, 0}, Arrays.copyOfRange(esc, esc.length - 4, esc.length));
        assertEquals(2, countCuts(esc));
        String asText = new String(esc, java.nio.charset.StandardCharsets.US_ASCII);
        assertTrue(asText.contains("TAVOLO 5"));

        byte[] star = PrinterEncoder.starPrnt(lines, 1);
        assertArrayEquals(new byte[]{0x1B, '@'}, Arrays.copyOfRange(star, 0, 2));
        assertArrayEquals(new byte[]{0x1B, 'd', 3}, Arrays.copyOfRange(star, star.length - 3, star.length));
    }

    @Test
    void testTicketFitsWidth() {
        for (String l : TicketRenderer.renderTest("Ristorante", "Cucina", 32, AT)) {
            assertTrue(TicketRenderer.textOf(l).length() <= 32, l);
        }
    }

    private static int countCuts(byte[] b) {
        int n = 0;
        for (int i = 0; i + 3 < b.length; i++) if (b[i] == 0x1D && b[i + 1] == 'V' && b[i + 2] == 66) n++;
        return n;
    }

    private static int indexOfStartingWith(List<String> list, String prefix) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).startsWith(prefix)) return i;
        return -1;
    }
}
