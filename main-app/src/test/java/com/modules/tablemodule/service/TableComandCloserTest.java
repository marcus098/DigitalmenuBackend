package com.modules.tablemodule.service;

import com.modules.common.finders.OrderUtils;
import com.modules.common.model.Comand;
import com.modules.common.model.enums.ComandStatus;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import com.modules.ordermodule.model.ComandJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chiusura del tavolo: ordini QR mai accettati → DELETED (con evento per i pagamenti), accettati → COMPLETED. */
class TableComandCloserTest {

    private OrderUtils orderUtils;
    private ApplicationEventPublisher events;
    private TableComandCloser closer;

    @BeforeEach
    void setUp() {
        orderUtils = mock(OrderUtils.class);
        events = mock(ApplicationEventPublisher.class);
        closer = new TableComandCloser();
        ReflectionTestUtils.setField(closer, "orderUtils", orderUtils);
        ReflectionTestUtils.setField(closer, "eventPublisher", events);
        when(orderUtils.updateStatusIfIn(anyList(), anyLong(), anyList(), any())).thenReturn(1L);
    }

    private static Comand comand(String id, ComandStatus status) {
        ComandJpa c = new ComandJpa();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    @Test
    void ordiniMaiAccettatiAnnullati_accettatiCompletati() {
        when(orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(eq("sess-1"), eq(7L), anyList())).thenReturn(List.of(
                comand("qr", ComandStatus.AWAIT),
                comand("prepay", ComandStatus.AWAIT_PAYMENT),
                comand("accettata", ComandStatus.PENDING),
                comand("cucina", ComandStatus.PROGRESS)));

        TableComandCloser.Result r = closer.closeSessionComands("sess-1", 7L, 1L, "test");

        assertEquals(2, r.cancelled());
        assertEquals(2, r.completed());
        verify(orderUtils).updateStatusIfIn(List.of("qr"), 7L, List.of(ComandStatus.AWAIT), ComandStatus.DELETED);
        verify(orderUtils).updateStatusIfIn(List.of("prepay"), 7L, List.of(ComandStatus.AWAIT_PAYMENT), ComandStatus.DELETED);
        verify(orderUtils).updateStatusIfIn(List.of("accettata"), 7L, List.of(ComandStatus.PENDING), ComandStatus.COMPLETED);
        verify(orderUtils).updateStatusIfIn(List.of("cucina"), 7L, List.of(ComandStatus.PROGRESS), ComandStatus.COMPLETED);

        // l'evento (annullamento autorizzazioni di pagamento) solo per le comande annullate
        ArgumentCaptor<Object> ev = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(ev.capture());
        List<String> ids = ev.getAllValues().stream().map(e -> ((ComandStatusChangedEvent) e).comandId()).toList();
        assertEquals(List.of("qr", "prepay"), ids);
        ev.getAllValues().forEach(e -> assertEquals(ComandStatus.DELETED, ((ComandStatusChangedEvent) e).newStatus()));
        verify(orderUtils).saveAllLogs(argThat(l -> l.size() == 4));
    }

    @Test
    void cercaAncheGliOrdiniInAttesaDiPagamento() {
        when(orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(any(), anyLong(), anyList())).thenReturn(List.of());
        closer.closeSessionComands("sess-1", 7L, 1L, "test");
        verify(orderUtils).findByTableSessionIdAndIdAgencyAndStatusIn(eq("sess-1"), eq(7L),
                argThat(l -> l.containsAll(List.of("AWAIT", "AWAIT_PAYMENT", "PENDING", "PROGRESS"))));
    }

    @Test
    void transizioneConcorrente_nessunEventoNeLog() {
        // il cameriere ha accettato l'ordine un attimo prima: l'update condizionale su AWAIT non trova nulla
        when(orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(any(), anyLong(), anyList()))
                .thenReturn(List.of(comand("qr", ComandStatus.AWAIT)));
        when(orderUtils.updateStatusIfIn(anyList(), anyLong(), anyList(), any())).thenReturn(0L);

        TableComandCloser.Result r = closer.closeSessionComands("sess-1", 7L, 1L, "test");

        assertFalse(r.any());
        verifyNoInteractions(events);
        verify(orderUtils, never()).saveAllLogs(anyList());
    }

    @Test
    void senzaSessione_nessunaQuery() {
        assertFalse(closer.closeSessionComands("", 7L, 1L, "test").any());
        assertFalse(closer.closeSessionComands(null, 7L, 1L, "test").any());
        verifyNoInteractions(orderUtils, events);
    }
}
