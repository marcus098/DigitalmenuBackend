package com.modules.printmodule.render;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Converte le righe del TicketRenderer nei byte da inviare alla stampante.
 * Il testo è già ASCII puro (vedi TicketRenderer.sanitize) quindi non serve selezionare una code page.
 */
public final class PrinterEncoder {

    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final byte LF = 0x0A;

    private PrinterEncoder() {}

    /** ESC/POS (Epson-compatibile: Rongta, Xprinter, Munbyn...). Ogni copia termina con taglio parziale. */
    public static byte[] escPos(List<String> lines, int copies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int c = 0; c < Math.max(1, copies); c++) {
            out.writeBytes(new byte[]{ESC, '@'});            // init
            out.writeBytes(new byte[]{ESC, 't', 0});          // code page PC437 (solo ASCII usato)
            for (String line : lines) {
                String style = TicketRenderer.styleOf(line);
                byte[] text = ascii(TicketRenderer.textOf(line));
                switch (style) {
                    case TicketRenderer.HUGE -> {
                        out.writeBytes(new byte[]{ESC, 'a', 1, GS, '!', 0x11, ESC, 'E', 1});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{GS, '!', 0, ESC, 'E', 0, ESC, 'a', 0});
                    }
                    case TicketRenderer.CENTER -> {
                        out.writeBytes(new byte[]{ESC, 'a', 1, ESC, 'E', 1});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{ESC, 'E', 0, ESC, 'a', 0});
                    }
                    case TicketRenderer.BOLD -> {
                        out.writeBytes(new byte[]{ESC, 'E', 1});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{ESC, 'E', 0});
                    }
                    default -> { out.writeBytes(text); out.write(LF); }
                }
            }
            out.writeBytes(new byte[]{ESC, 'd', 4});          // avanza 4 righe
            out.writeBytes(new byte[]{GS, 'V', 66, 0});       // feed + taglio parziale
        }
        return out.toByteArray();
    }

    /** StarPRNT (application/vnd.star.starprnt) per CloudPRNT su mC-Print2/3, TSP143IV. */
    public static byte[] starPrnt(List<String> lines, int copies) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int c = 0; c < Math.max(1, copies); c++) {
            out.writeBytes(new byte[]{ESC, '@'});
            for (String line : lines) {
                String style = TicketRenderer.styleOf(line);
                byte[] text = ascii(TicketRenderer.textOf(line));
                switch (style) {
                    case TicketRenderer.HUGE -> {
                        out.writeBytes(new byte[]{ESC, GS, 'a', 1, ESC, 'i', 1, 1, ESC, 'E'});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{ESC, 'i', 0, 0, ESC, 'F', ESC, GS, 'a', 0});
                    }
                    case TicketRenderer.CENTER -> {
                        out.writeBytes(new byte[]{ESC, GS, 'a', 1, ESC, 'E'});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{ESC, 'F', ESC, GS, 'a', 0});
                    }
                    case TicketRenderer.BOLD -> {
                        out.writeBytes(new byte[]{ESC, 'E'});
                        out.writeBytes(text); out.write(LF);
                        out.writeBytes(new byte[]{ESC, 'F'});
                    }
                    default -> { out.writeBytes(text); out.write(LF); }
                }
            }
            out.writeBytes(new byte[]{ESC, 'd', 3});          // feed + taglio parziale
        }
        return out.toByteArray();
    }

    /** text/plain: copie separate da righe vuote (la stampante Star taglia a fine job). */
    public static byte[] plainText(List<String> lines, int width, int copies) {
        String one = TicketRenderer.toPlainText(lines, width);
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < Math.max(1, copies); c++) {
            if (c > 0) sb.append("\n\n\n\n");
            sb.append(one);
        }
        sb.append("\n\n\n");
        return ascii(sb.toString());
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
