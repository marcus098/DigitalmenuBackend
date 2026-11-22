package com.modules.tablemodule.service;

import com.modules.common.finders.OrderUtils;
import com.modules.common.model.Comand;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.tablemodule.model.TableEntityJpa;
import com.modules.tablemodule.repository.TableEntityRepository;
import com.modules.tablemodule.repository.mongo.MongoTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TableServiceDeleteTest {

    private TableEntityRepository tables;
    private OrderUtils orderUtils;
    private TableService service;

    @BeforeEach
    void setUp() {
        tables = mock(TableEntityRepository.class);
        orderUtils = mock(OrderUtils.class);
        AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);
        when(auth.getUserId()).thenReturn(1L);
        when(auth.getAgencyId()).thenReturn(7L);
        service = new TableService();
        ReflectionTestUtils.setField(service, "tableEntityRepository", tables);
        ReflectionTestUtils.setField(service, "mongoTableRepository", mock(MongoTableRepository.class));
        ReflectionTestUtils.setField(service, "authUserProvider", auth);
        ReflectionTestUtils.setField(service, "orderUtils", orderUtils);
    }

    private TableEntityJpa table(String sessionId) {
        TableEntityJpa t = new TableEntityJpa("D05", 7L, 0, 0, 3, 3, "Sala");
        t.setId(16L);
        t.setSessionId(sessionId);
        return t;
    }

    @Test
    void tavoloMaiAperto_siEliminaSenzaInterrogareLeComande() {
        TableEntityJpa t = table(null);
        when(tables.findByIdAndIdAgencyAndDeleted(16L, 7L, false)).thenReturn(Optional.of(t));
        assertEquals(200, service.deleteTable(16L));
        assertTrue(t.isDeleted());
        assertNotNull(t.getDeletedAt());
        verifyNoInteractions(orderUtils);
    }

    @Test
    void tavoloConOrdiniAperti_409() {
        TableEntityJpa t = table("sess-1");
        when(tables.findByIdAndIdAgencyAndDeleted(16L, 7L, false)).thenReturn(Optional.of(t));
        when(orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(eq("sess-1"), eq(7L), anyList()))
                .thenReturn(List.of(mock(Comand.class)));
        assertEquals(409, service.deleteTable(16L));
        assertFalse(t.isDeleted());
    }

    @Test
    void tavoloConSessioneSenzaOrdiniAperti_siElimina() {
        TableEntityJpa t = table("sess-1");
        when(tables.findByIdAndIdAgencyAndDeleted(16L, 7L, false)).thenReturn(Optional.of(t));
        when(orderUtils.findByTableSessionIdAndIdAgencyAndStatusIn(any(), anyLong(), anyList())).thenReturn(List.of());
        assertEquals(200, service.deleteTable(16L));
    }

    @Test
    void tavoloInesistente_404() {
        when(tables.findByIdAndIdAgencyAndDeleted(99L, 7L, false)).thenReturn(Optional.empty());
        assertEquals(404, service.deleteTable(99L));
    }
}
