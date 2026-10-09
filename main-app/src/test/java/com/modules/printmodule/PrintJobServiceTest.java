package com.modules.printmodule;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.printmodule.model.*;
import com.modules.printmodule.repository.PrintJobRepository;
import com.modules.printmodule.repository.PrinterRepository;
import com.modules.printmodule.service.PrintJobService;
import com.modules.printmodule.service.TicketFactory;
import com.modules.tablemodule.repository.TableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrintJobServiceTest {

    private PrinterRepository printerRepo;
    private PrintJobRepository jobRepo;
    private MongoComandReadRepository comandRepo;
    private PrintJobService service;
    private ComandJpa comand;

    @BeforeEach
    void setUp() {
        printerRepo = mock(PrinterRepository.class);
        jobRepo = mock(PrintJobRepository.class);
        comandRepo = mock(MongoComandReadRepository.class);
        TicketFactory factory = new TicketFactory(mock(AgencyRepository.class), mock(TableEntityRepository.class));
        service = new PrintJobService(printerRepo, jobRepo, comandRepo, factory);
        when(jobRepo.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        comand = new ComandJpa();
        comand.setId("c1");
        comand.setIdAgency(7L);
        comand.setStatus(ComandStatus.AWAIT);
        comand.setComandWaiterType(ComandWaiterType.TABLE);
        comand.setIdTable(3L);
        comand.setCreatedAt(LocalDateTime.now());
        comand.setOrders(List.of(new Order("o1", LocalDateTime.now(), LocalDateTime.now(), "c1", "0", List.of(
                new ProductToOrder(1, "Margherita", 10, "Pizze", null, 2, null),
                new ProductToOrder(2, "Birra", 20, "Bevande", null, 1, null)))));
        when(comandRepo.findById("c1")).thenReturn(Optional.of(comand));
    }

    private PrinterDoc printer(String id, PrintOn printOn, List<String> filter) {
        PrinterDoc p = new PrinterDoc();
        p.setId(id);
        p.setIdAgency(7L);
        p.setName(id);
        p.setType(PrinterType.ESCPOS_BRIDGE);
        p.setPrintOn(printOn);
        p.setCategoryFilter(filter);
        return p;
    }

    @Test
    void createdEventRespectsPrintOnAndCategoryFilter() {
        PrinterDoc kitchen = printer("kitchen", PrintOn.CREATED, List.of("10"));      // per id categoria
        PrinterDoc bar = printer("bar", PrintOn.CREATED, List.of("bevande"));          // per nome (case-insensitive)
        PrinterDoc desserts = printer("desserts", PrintOn.CREATED, List.of("Dolci"));  // nessun prodotto → skip
        PrinterDoc onAccept = printer("accept", PrintOn.ACCEPTED, List.of());
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(kitchen, bar, desserts, onAccept));

        int n = service.enqueueForComand("c1", 7L, PrintJobService.Trigger.CREATED);

        assertEquals(2, n);
        ArgumentCaptor<PrintJobDoc> captor = ArgumentCaptor.forClass(PrintJobDoc.class);
        verify(jobRepo, times(2)).insert(captor.capture());
        PrintJobDoc k = captor.getAllValues().get(0);
        assertEquals("kitchen", k.getPrinterId());
        assertEquals("kitchen:c1:NEW_ORDER", k.getDedupKey());
        assertTrue(k.getContent().contains("B|2x Margherita"));
        assertFalse(k.getContent().stream().anyMatch(l -> l.contains("Birra")));
        assertTrue(k.getContent().contains("H|TAVOLO 3")); // tavolo non trovato → id
        assertTrue(captor.getAllValues().get(1).getContent().contains("B|1x Birra"));
    }

    @Test
    void acceptedTriggerOnlyForAcceptedPrinters() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(
                printer("a", PrintOn.CREATED, List.of()), printer("b", PrintOn.ACCEPTED, List.of())));
        comand.setStatus(ComandStatus.PROGRESS);
        assertEquals(1, service.enqueueForComand("c1", 7L, PrintJobService.Trigger.ACCEPTED));
    }

    @Test
    void duplicateNewOrderIsIgnoredAndOtherAgencySkipped() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(printer("a", PrintOn.CREATED, List.of())));
        when(jobRepo.insert(any())).thenThrow(new DuplicateKeyException("dup"));
        assertEquals(0, service.enqueueForComand("c1", 7L, PrintJobService.Trigger.CREATED));
        assertEquals(0, service.enqueueForComand("c1", 99L, PrintJobService.Trigger.CREATED));
    }

    @Test
    void reprintHasBannerAndNoDedupKey() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(printer("a", PrintOn.CREATED, List.of())));
        assertEquals(Optional.of(1), service.reprint("c1", 7L, null));
        ArgumentCaptor<PrintJobDoc> captor = ArgumentCaptor.forClass(PrintJobDoc.class);
        verify(jobRepo).insert(captor.capture());
        assertEquals(PrintJobKind.REPRINT, captor.getValue().getKind());
        assertNull(captor.getValue().getDedupKey());
        assertTrue(captor.getValue().getContent().contains("C|*** RISTAMPA ***"));
        assertTrue(service.reprint("c1", 99L, null).isEmpty());
    }

    private PrinterDoc tablet(String id, List<String> filter, int copies) {
        PrinterDoc p = printer(id, PrintOn.CREATED, filter);
        p.setType(PrinterType.TABLET_RAWBT);
        p.setCopies(copies);
        return p;
    }

    private static int count(byte[] haystack, byte[] needle) {
        int n = 0;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            n++;
        }
        return n;
    }

    @Test
    void tabletPrintersAreNeverQueued() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(
                printer("bridge", PrintOn.CREATED, List.of()), tablet("tab", List.of(), 1)));
        assertEquals(1, service.enqueueForComand("c1", 7L, PrintJobService.Trigger.CREATED));
        assertEquals(Optional.of(1), service.reprint("c1", 7L, null));
        ArgumentCaptor<PrintJobDoc> captor = ArgumentCaptor.forClass(PrintJobDoc.class);
        verify(jobRepo, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(j -> j.getPrinterId().equals("bridge")));

        PrinterDoc tab = tablet("tab", List.of(), 1);
        when(printerRepo.findByIdAndIdAgency("tab", 7L)).thenReturn(Optional.of(tab));
        assertEquals(Optional.of(0), service.reprint("c1", 7L, "tab"));
        assertThrows(IllegalStateException.class, () -> service.enqueueTest(tab));
        verify(jobRepo, times(2)).insert(any());
    }

    @Test
    void tabletTicketConcatenatesPerPrinterTicketsWithCategoryFilter() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(
                tablet("kitchen", List.of("Pizze"), 2),
                tablet("bar", List.of("Bevande"), 1),
                tablet("desserts", List.of("Dolci"), 1),          // nessun prodotto → nessuno scontrino
                printer("bridge", PrintOn.CREATED, List.of())));  // non tablet → ignorata

        PrintJobService.TabletTicket t = service.tabletTicket("c1", 7L, false);

        assertEquals(PrintJobService.TabletOutcome.OK, t.outcome());
        assertEquals(2, t.tickets());
        String text = new String(t.escPos(), java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals(2, count(t.escPos(), "2x Margherita".getBytes()));   // copie = 2
        assertEquals(1, count(t.escPos(), "1x Birra".getBytes()));
        assertEquals(3, count(t.escPos(), new byte[]{0x1D, 'V', 66, 0})); // 3 tagli: 2 cucina + 1 bar
        assertTrue(text.indexOf("Margherita") < text.indexOf("Birra"));
        assertFalse(text.contains("RISTAMPA"));

        // storico: un job PRINTED per stampante, senza dedupKey
        ArgumentCaptor<PrintJobDoc> captor = ArgumentCaptor.forClass(PrintJobDoc.class);
        verify(jobRepo, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(j ->
                j.getStatus() == PrintJobStatus.PRINTED && j.getDedupKey() == null && j.getKind() == PrintJobKind.NEW_ORDER));

        PrintJobService.TabletTicket r = service.tabletTicket("c1", 7L, true);
        assertTrue(new String(r.escPos(), java.nio.charset.StandardCharsets.US_ASCII).contains("*** RISTAMPA ***"));
    }

    @Test
    void tabletTicketOutcomes() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(printer("bridge", PrintOn.CREATED, List.of())));
        assertEquals(PrintJobService.TabletOutcome.NO_CONTENT, service.tabletTicket("c1", 7L, false).outcome());
        assertEquals(PrintJobService.TabletOutcome.NOT_FOUND, service.tabletTicket("c1", 99L, false).outcome());

        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(tablet("dolci", List.of("Dolci"), 1)));
        assertEquals(PrintJobService.TabletOutcome.NO_CONTENT, service.tabletTicket("c1", 7L, false).outcome());

        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(tablet("tab", List.of(), 1)));
        comand.setStatus(ComandStatus.AWAIT_PAYMENT);
        assertEquals(PrintJobService.TabletOutcome.CONFLICT, service.tabletTicket("c1", 7L, false).outcome());
        comand.setStatus(ComandStatus.DELETED);
        assertEquals(PrintJobService.TabletOutcome.CONFLICT, service.tabletTicket("c1", 7L, false).outcome());
        verify(jobRepo, never()).insert(any());
    }
}
