package com.modules.printmodule;

import com.modules.authmodule.repository.AgencyRepository;
import com.modules.common.model.ComandItemChange;
import com.modules.common.model.Order;
import com.modules.common.model.ProductToOrder;
import com.modules.common.model.enums.ComandItemChangeType;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Scontrino "VARIAZIONE ORDINE": solo le differenze, solo alle stampanti che hanno già la comanda. */
class PrintVariationTest {

    private PrinterRepository printerRepo;
    private PrintJobRepository jobRepo;
    private PrintJobService service;
    private ComandJpa comand;
    private List<ComandItemChange> changes;

    @BeforeEach
    void setUp() {
        printerRepo = mock(PrinterRepository.class);
        jobRepo = mock(PrintJobRepository.class);
        MongoComandReadRepository comandRepo = mock(MongoComandReadRepository.class);
        service = new PrintJobService(printerRepo, jobRepo, comandRepo,
                new TicketFactory(mock(AgencyRepository.class), mock(TableEntityRepository.class)));
        when(jobRepo.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        comand = new ComandJpa();
        comand.setId("c1");
        comand.setIdAgency(7L);
        comand.setStatus(ComandStatus.PROGRESS);
        comand.setComandWaiterType(ComandWaiterType.TABLE);
        comand.setIdTable(3L);
        comand.setCreatedAt(LocalDateTime.now());
        comand.setOrders(List.of(new Order("o1", LocalDateTime.now(), LocalDateTime.now(), "c1", "0", List.of(
                new ProductToOrder(1, "Margherita", 10, "Pizze", null, 3, null)))));
        when(comandRepo.findById("c1")).thenReturn(Optional.of(comand));

        ComandItemChange removed = new ComandItemChange(ComandItemChangeType.REMOVED, 2, "Diavola", 10, "Pizze");
        removed.setOldQty(1);
        ComandItemChange qty = new ComandItemChange(ComandItemChangeType.QTY_CHANGED, 1, "Margherita", 10, "Pizze");
        qty.setOldQty(2);
        qty.setNewQty(3);
        ComandItemChange variant = new ComandItemChange(ComandItemChangeType.VARIANT_CHANGED, 4, "Capricciosa", 10, "Pizze");
        variant.setOldQty(1);
        variant.setNewQty(1);
        variant.setOldDescription("Normale");
        variant.setNewDescription("Normale, senza Funghi");
        ComandItemChange beer = new ComandItemChange(ComandItemChangeType.ADDED, 5, "Birra", 20, "Bevande");
        beer.setNewQty(2);
        changes = List.of(removed, qty, variant, beer);
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

    private List<PrintJobDoc> inserted(int times) {
        ArgumentCaptor<PrintJobDoc> captor = ArgumentCaptor.forClass(PrintJobDoc.class);
        verify(jobRepo, times(times)).insert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void inCucina_soloLeDifferenzeDelleSueCategorie() {
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(
                printer("cucina", PrintOn.ACCEPTED, List.of("Pizze")), printer("bar", PrintOn.ACCEPTED, List.of("Bevande"))));

        assertEquals(2, service.enqueueVariation("c1", 7L, changes, "Mario Rossi", LocalDateTime.of(2026, 11, 26, 21, 5)));

        List<PrintJobDoc> jobs = inserted(2);
        PrintJobDoc kitchen = jobs.get(0);
        assertEquals(PrintJobKind.VARIATION, kitchen.getKind());
        assertNull(kitchen.getDedupKey()); // più variazioni della stessa comanda
        List<String> lines = kitchen.getContent();
        assertTrue(lines.contains("H|TAVOLO 3"), lines.toString());
        assertTrue(lines.contains("C|*** VARIAZIONE ORDINE ***"), lines.toString());
        assertTrue(lines.contains("B|Modificato da Mario Rossi alle 21:05"), lines.toString());
        assertTrue(lines.contains("I|- 1x Diavola"), lines.toString());
        assertTrue(lines.contains("B|~ Margherita: 2x -> 3x"), lines.toString());
        assertTrue(lines.contains("B|~ 1x Capricciosa"), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("prima: Normale")), lines.toString());
        assertTrue(lines.stream().anyMatch(l -> l.contains("ora: Normale, senza Funghi")), lines.toString());
        assertFalse(lines.stream().anyMatch(l -> l.contains("Birra")));
        assertTrue(jobs.get(1).getContent().contains("B|+ 2x Birra"));
    }

    @Test
    void nonAncoraInCucina_nessunaStampa() {
        comand.setStatus(ComandStatus.AWAIT);
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(printer("cucina", PrintOn.ACCEPTED, List.of())));
        when(jobRepo.existsForComand(anyString(), anyString())).thenReturn(false);
        assertEquals(0, service.enqueueVariation("c1", 7L, changes, "Mario", LocalDateTime.now()));
        verify(jobRepo, never()).insert(any());
    }

    @Test
    void giaStampataAllaCreazione_oRistampata() {
        comand.setStatus(ComandStatus.AWAIT);
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(
                printer("creazione", PrintOn.CREATED, List.of()), printer("ristampata", PrintOn.ACCEPTED, List.of())));
        when(jobRepo.existsForComand("ristampata", "c1")).thenReturn(true);
        assertEquals(2, service.enqueueVariation("c1", 7L, changes, "Mario", LocalDateTime.now()));
    }

    @Test
    void daApprovare_nessunaStampa_eAltroLocaleIgnorato() {
        comand.setStatus(ComandStatus.AWAIT_APPROVAL);
        when(printerRepo.findEnabledByIdAgency(7L)).thenReturn(List.of(printer("a", PrintOn.CREATED, List.of())));
        assertEquals(0, service.enqueueVariation("c1", 7L, changes, "Mario", LocalDateTime.now()));
        comand.setStatus(ComandStatus.PROGRESS);
        assertEquals(0, service.enqueueVariation("c1", 99L, changes, "Mario", LocalDateTime.now()));
        verify(jobRepo, never()).insert(any());
    }
}
