package com.modules.ordermodule.service;

import com.modules.common.dto.TableDto;
import com.modules.common.finders.TableUtils;
import com.modules.common.model.enums.SessionStatus;
import com.modules.ordermodule.kafka.TableSessionUpdateProducer;
import com.modules.ordermodule.model.TableSessionJpa;
import com.modules.ordermodule.repository.TableSessionRepository;
import com.modules.tablemodule.service.TableComandCloser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chiusura del tavolo dalla sessione gruppo: un tavolo occupato si deve poter chiudere anche senza sessione Mongo. */
class TableSessionCloseTest {

    private TableSessionRepository sessions;
    private TableUtils tableUtils;
    private TableSessionService service;
    private TableComandCloser closer;

    @BeforeEach
    void setUp() {
        sessions = mock(TableSessionRepository.class);
        tableUtils = mock(TableUtils.class);
        service = new TableSessionService();
        ReflectionTestUtils.setField(service, "tableSessionRepository", sessions);
        ReflectionTestUtils.setField(service, "tableUtils", tableUtils);
        ReflectionTestUtils.setField(service, "tableSessionUpdateProducer", mock(TableSessionUpdateProducer.class));
        closer = mock(TableComandCloser.class);
        when(closer.closeSessionComands(any(), anyLong(), anyLong(), any())).thenReturn(new TableComandCloser.Result(1, 0));
        ReflectionTestUtils.setField(service, "tableComandCloser", closer);
        when(sessions.findByTableIdAndStatus(anyLong(), eq(SessionStatus.OPEN))).thenReturn(Optional.empty());
    }

    private TableDto table(boolean busy, String sessionId) {
        TableDto t = new TableDto();
        t.setId(12L);
        t.setIdAgency(7L);
        t.setBusy(busy);
        t.setSeats(4);
        t.setCode("AB12");
        t.setSessionId(sessionId);
        return t;
    }

    @Test
    void tavoloOccupatoSenzaSessione_vieneLiberato() {
        TableDto t = table(true, "");
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.of(t));

        Map<String, Object> resp = service.closeSession(12L, 7L);

        assertEquals(200, TableSessionService.extractStatus(resp));
        assertEquals("CLOSED", resp.get("status"));
        assertFalse(t.isBusy());
        assertEquals(0, t.getSeats());
        assertEquals("", t.getCode());
        verify(tableUtils).save(t, 7L);
    }

    @Test
    void tavoloConSessioneFissataMaDocumentoAssente_vieneLiberato() {
        TableDto t = table(true, "sess-perduta");
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.of(t));
        when(sessions.findById("sess-perduta")).thenReturn(Optional.empty());

        Map<String, Object> resp = service.closeSession(12L, 7L);

        assertEquals(200, TableSessionService.extractStatus(resp));
        assertFalse(t.isBusy());
        assertEquals("", t.getSessionId());
    }

    @Test
    void tavoloGiaLibero_404() {
        TableDto t = table(false, "");
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.of(t));

        assertEquals(404, TableSessionService.extractStatus(service.closeSession(12L, 7L)));
        verify(tableUtils, never()).save(any(), anyLong());
    }

    @Test
    void tavoloDiAltroLocale_404() {
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.empty());
        assertEquals(404, TableSessionService.extractStatus(service.closeSession(12L, 7L)));
    }

    private TableSessionJpa openSession(String id) {
        TableSessionJpa s = new TableSessionJpa();
        s.setId(id);
        s.setTableId(12L);
        s.setIdAgency(7L);
        s.setStatus(SessionStatus.OPEN);
        return s;
    }

    /** POST /api/tables/{id}/session/close: gli ordini QR mai accettati della sessione vanno chiusi come /freeAndClose. */
    @Test
    void chiusuraSessione_chiudeLeComandeDellaSessione() {
        TableSessionJpa session = openSession("sess-qr");
        when(sessions.findByTableIdAndStatus(12L, SessionStatus.OPEN)).thenReturn(Optional.of(session));
        when(sessions.findById("sess-qr")).thenReturn(Optional.of(session));
        TableDto t = table(true, "sess-qr");
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.of(t));

        Map<String, Object> resp = service.closeSession(12L, 7L, 4L);

        assertEquals(200, TableSessionService.extractStatus(resp));
        assertEquals(SessionStatus.CLOSED, session.getStatus());
        verify(closer).closeSessionComands(eq("sess-qr"), eq(7L), eq(4L), any());
        assertFalse(t.isBusy());
    }

    @Test
    void chiusuraSenzaDocumentoSessione_chiudeLeComandeDelSessionIdDelTavolo() {
        TableDto t = table(true, "sess-perduta");
        when(tableUtils.findByIdAndIdAgencyAndDeleted(12L, 7L)).thenReturn(Optional.of(t));
        when(sessions.findById("sess-perduta")).thenReturn(Optional.empty());

        service.closeSession(12L, 7L, 4L);

        verify(closer).closeSessionComands(eq("sess-perduta"), eq(7L), eq(4L), any());
    }

    @Test
    void closeSessionById_chiudeLeComande() {
        TableSessionJpa session = openSession("sess-qr");
        when(sessions.findById("sess-qr")).thenReturn(Optional.of(session));

        service.closeSessionById("sess-qr", 7L);

        assertEquals(SessionStatus.CLOSED, session.getStatus());
        verify(closer).closeSessionComands(eq("sess-qr"), eq(7L), anyLong(), any());
    }
}
