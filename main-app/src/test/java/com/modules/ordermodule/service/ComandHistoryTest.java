package com.modules.ordermodule.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modules.cardmodule.service.CardService;
import com.modules.common.dto.UserDto;
import com.modules.common.model.ComandHistoryEntry;
import com.modules.common.model.ComandTakenBy;
import com.modules.common.model.enums.ComandHistoryAction;
import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandLogRepository;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.request.CheckoutRequest;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Storico delle comande: ogni cambio di stato (cambio stato, approva, rifiuta, cassa) aggiunge chi e da/a cosa. */
class ComandHistoryTest {

    private OrderComandService service;
    private MongoComandReadRepository reads;
    private ComandStatusUpdater updater;
    private AuthenticatedUserProvider auth;
    private ComandJpa comand;

    @BeforeEach
    void setUp() {
        service = new OrderComandService();
        reads = mock(MongoComandReadRepository.class);
        updater = mock(ComandStatusUpdater.class);
        auth = mock(AuthenticatedUserProvider.class);
        ReflectionTestUtils.setField(service, "mongoComandReadRepository", reads);
        ReflectionTestUtils.setField(service, "statusUpdater", updater);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "orderUpdateProducer", mock(OrderUpdateProducer.class));
        ReflectionTestUtils.setField(service, "takeawaySlotService", mock(TakeawaySlotService.class));
        ReflectionTestUtils.setField(service, "mongoComandLogRepository", mock(MongoComandLogRepository.class));
        ReflectionTestUtils.setField(service, "authUserProvider", auth);

        UserDto anna = new UserDto();
        anna.setId(12L);
        anna.setUsername("anna");
        anna.setName("Anna");
        anna.setSurname("Verdi");
        anna.setIdAgency(3L);
        when(auth.getAuthenticatedUser()).thenReturn(anna);
        when(auth.getUserId()).thenReturn(12L);
        when(auth.getAgencyId()).thenReturn(3L);

        comand = new ComandJpa();
        comand.setId("cmd1");
        comand.setIdAgency(3L);
        comand.setComandWaiterType(ComandWaiterType.TABLE);
        comand.setCreatedAt(LocalDateTime.now());
        comand.setOrders(new ArrayList<>());
        when(reads.findById("cmd1")).thenAnswer(inv -> Optional.of(comand));
        when(updater.compareAndSet(any(), any(), any(), any(), any())).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private ComandHistoryEntry onlyPushed() {
        ArgumentCaptor<Consumer<Update>> extra = ArgumentCaptor.forClass(Consumer.class);
        verify(updater).compareAndSet(eq("cmd1"), any(), any(), any(), extra.capture());
        List<ComandHistoryEntry> pushed = HistoryUpdates.pushed(extra.getValue());
        assertEquals(1, pushed.size());
        return pushed.get(0);
    }

    private static void assertStatus(ComandHistoryEntry e, ComandStatus from, ComandStatus to, Long byId, String byName) {
        assertEquals(ComandHistoryAction.STATUS, e.getAction());
        assertEquals(from, e.getFrom());
        assertEquals(to, e.getTo());
        assertEquals(byId, e.getBy().getId());
        assertEquals(byName, e.getBy().getName());
        assertNotNull(e.getAt());
    }

    @Test
    void cambioStato_registraChiEDaACosa() {
        comand.setStatus(ComandStatus.AWAIT);
        assertEquals(200, service.changeStatusToComand("cmd1", ComandStatus.PROGRESS).join());
        assertStatus(onlyPushed(), ComandStatus.AWAIT, ComandStatus.PROGRESS, 12L, "Anna Verdi");
    }

    @Test
    void approvazione() {
        comand.setStatus(ComandStatus.AWAIT_APPROVAL);
        service.approve("cmd1");
        assertStatus(onlyPushed(), ComandStatus.AWAIT_APPROVAL, ComandStatus.PENDING, 12L, "Anna Verdi");
    }

    @Test
    void rifiuto() {
        comand.setStatus(ComandStatus.AWAIT_APPROVAL);
        service.reject("cmd1", "Chiusi");
        assertStatus(onlyPushed(), ComandStatus.AWAIT_APPROVAL, ComandStatus.DELETED, 12L, "Anna Verdi");
    }

    @Test
    void rifiutoAutomatico_daSistema() {
        comand.setStatus(ComandStatus.AWAIT_APPROVAL);
        MongoTemplate mongo = mock(MongoTemplate.class);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        when(mongo.find(any(Query.class), eq(ComandJpa.class), anyString())).thenReturn(List.of(comand));
        assertEquals(1, service.autoRejectExpiredApprovals(LocalDateTime.now()));
        assertStatus(onlyPushed(), ComandStatus.AWAIT_APPROVAL, ComandStatus.DELETED, null, ComandHistoryEntry.SYSTEM_NAME);
    }

    @Test
    void chiusuraInCassa_voceCheckout() {
        comand.setStatus(ComandStatus.COMPLETED);
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), anyString()))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        CheckoutService checkout = new CheckoutService(reads, service, mock(CardService.class), mongo, auth);

        checkout.checkout("cmd1", new CheckoutRequest(1000, 1000, "PCT", 0.0, 0, null, 0, false, false));

        ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).updateFirst(any(Query.class), u.capture(), anyString());
        List<ComandHistoryEntry> pushed = HistoryUpdates.pushed((Update) u.getValue());
        assertEquals(1, pushed.size());
        assertEquals(ComandHistoryAction.CHECKOUT, pushed.get(0).getAction());
        assertEquals("Anna Verdi", pushed.get(0).getBy().getName());
    }

    @Test
    void comandePrecedentiSenzaStorico_siLeggonoComunque() throws Exception {
        ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
        ComandJpa old = om.readValue("{\"id\":\"x\",\"status\":\"AWAIT\",\"idAgency\":3,\"orders\":[]}", ComandJpa.class);
        assertEquals(0L, old.getVersion());
        assertTrue(old.getHistory().isEmpty());

        // e in JSON escono version e history (lista vuota, mai null)
        String json = om.writeValueAsString(old);
        assertTrue(json.contains("\"version\":0"), json);
        assertTrue(json.contains("\"history\":[]"), json);

        ComandJpa withHistory = new ComandJpa();
        withHistory.appendHistory(ComandHistoryEntry.created(new ComandTakenBy(7L, "Mario"), ComandStatus.PROGRESS));
        ComandJpa back = om.readValue(om.writeValueAsString(withHistory), ComandJpa.class);
        assertEquals(ComandHistoryAction.CREATED, back.getHistory().get(0).getAction());
        assertEquals("Mario", back.getHistory().get(0).getBy().getName());
    }

    @Test
    void storicoLimitatoInMemoria() {
        ComandJpa c = new ComandJpa();
        for (int i = 0; i < ComandHistoryEntry.MAX_ENTRIES + 5; i++) {
            c.appendHistory(ComandHistoryEntry.status(ComandHistoryEntry.system(), ComandStatus.AWAIT, ComandStatus.PROGRESS));
        }
        assertEquals(ComandHistoryEntry.MAX_ENTRIES, c.getHistory().size());
    }
}
