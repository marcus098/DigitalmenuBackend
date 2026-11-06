package com.modules.printmodule;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.common.model.variant.SelectedVariant;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.printmodule.model.PrintOn;
import com.modules.printmodule.model.PrinterDoc;
import com.modules.printmodule.model.PrinterType;
import com.modules.printmodule.render.PrinterEncoder;
import com.modules.printmodule.render.TicketData;
import com.modules.printmodule.render.TicketRenderer;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.TicketFactory;
import com.modules.tablemodule.repository.TableEntityRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Varianti sulla comanda stampata: una riga ciascuna, quelle highlight in evidenza (negativo + "/!\"). */
class PrintVariantsTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 7, 20, 0);

    private static TicketData ticket() {
        return new TicketData("Trattoria", TicketData.OrderKind.TABLE, "3", null, null, null, null, AT, "ABC", null, List.of(
                new TicketData.Item("Pizze", 1, "Margherita", "Maxi", List.of(
                        new TicketData.Variant("Impasto", "Senza glutine", true),
                        new TicketData.Variant("Mozzarella", "Classica", false)),
                        List.of(), List.of("bufala"), null)));
    }

    @Test
    void eachVariantOnItsOwnLineHighlightInverted() {
        List<String> lines = TicketRenderer.render(ticket(), 48);
        int product = lines.indexOf("B|1x Margherita (Maxi)");
        assertTrue(product >= 0, lines.toString());
        assertEquals("I|   /!\\ SENZA GLUTINE", lines.get(product + 1));
        assertEquals("N|   > Mozzarella: Classica", lines.get(product + 2));
        assertEquals("N|   + bufala", lines.get(product + 3));
        for (String l : TicketRenderer.render(ticket(), 32)) assertTrue(TicketRenderer.textOf(l).length() <= 32);
    }

    @Test
    void encodersPrintHighlightInReverse() {
        List<String> lines = TicketRenderer.render(ticket(), 48);
        byte[] esc = PrinterEncoder.escPos(lines, 1);
        assertTrue(indexOf(esc, new byte[]{0x1D, 'B', 1}) >= 0, "ESC/POS: GS B 1 (negativo)");
        assertTrue(indexOf(esc, new byte[]{0x1D, 'B', 1}) < indexOf(esc, "SENZA GLUTINE".getBytes(StandardCharsets.US_ASCII)));
        byte[] star = PrinterEncoder.starPrnt(lines, 1);
        assertTrue(indexOf(star, new byte[]{0x1B, '4'}) >= 0, "StarPRNT: ESC 4 (negativo)");
        String plain = new String(PrinterEncoder.plainText(lines, 48, 1), StandardCharsets.US_ASCII);
        assertTrue(plain.contains("/!\\ SENZA GLUTINE"));
    }

    @Test
    void tabletTicketFromComandIncludesSelectedVariants() {
        PrinterRepository printerRepo = mock(PrinterRepository.class);
        PrintJobRepository jobRepo = mock(PrintJobRepository.class);
        MongoComandReadRepository comandRepo = mock(MongoComandReadRepository.class);
        TicketFactory factory = new TicketFactory(mock(AgencyRepository.class), mock(TableEntityRepository.class));
        PrintJobService service = new PrintJobService(printerRepo, jobRepo, comandRepo, factory);
        when(jobRepo.insert(any(com.modules.printmodule.model.PrintJobDoc.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductToOrder pizza = new ProductToOrder(44, "Margherita", 10, "Pizze", null, 1, null);
        pizza.setSelectedVariants(List.of(
                new SelectedVariant("impasto", "Impasto", "senza-glutine", "Senza glutine", 2.0, true),
                new SelectedVariant("mozzarella", "Mozzarella", "senza-lattosio", "Senza lattosio", 1.0, true)));
        ComandJpa comand = new ComandJpa();
        comand.setId("c1");
        comand.setIdAgency(7L);
        comand.setStatus(ComandStatus.AWAIT);
        comand.setComandWaiterType(ComandWaiterType.TAKE_AWAY);
        comand.setCreatedAt(AT);
        comand.setOrders(List.of(new Order("o1", AT, AT, "c1", "0", List.of(pizza))));
        when(comandRepo.findById("c1")).thenReturn(Optional.of(comand));

        PrinterDoc tab = new PrinterDoc();
        tab.setId("tab");
        tab.setIdAgency(7L);
        tab.setName("tab");
        tab.setType(PrinterType.TABLET_RAWBT);
        tab.setPrintOn(PrintOn.CREATED);
        tab.setCategoryFilter(List.of());
        tab.setCopies(1);
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(tab));

        PrintJobService.TabletTicket t = service.tabletTicket("c1", 7L, false);
        assertEquals(PrintJobService.TabletOutcome.OK, t.outcome());
        String esc = new String(t.escPos(), StandardCharsets.US_ASCII);
        assertTrue(esc.contains("/!\\ SENZA GLUTINE"));
        assertTrue(esc.contains("/!\\ SENZA LATTOSIO"));
        assertTrue(t.text().contains("SENZA GLUTINE"), t.text());
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return i;
        }
        return -1;
    }
}
