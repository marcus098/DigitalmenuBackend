package com.modules.ordermodule;

import com.modules.common.model.enums.ComandStatus;
import com.modules.common.model.enums.ComandWaiterType;
import com.modules.ordermodule.event.ComandCreatedEvent;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandReadRepository;
import com.modules.ordermodule.service.ComandStatusUpdater;
import com.modules.ordermodule.service.OrderComandService;
import com.modules.takeawaymodule.service.TakeawaySlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Transizioni AWAIT_PAYMENT → stato iniziale / AWAIT_APPROVAL: idempotenti, pipeline "creata" una sola volta. */
class PrepaymentTransitionTest {

    private OrderComandService service;
    private MongoComandReadRepository reads;
    private ComandStatusUpdater updater;
    private ApplicationEventPublisher publisher;
    private OrderUpdateProducer kafka;
    private TakeawaySlotService slots;

    @BeforeEach
    void setUp() {
        service = new OrderComandService();
        reads = mock(MongoComandReadRepository.class);
        updater = mock(ComandStatusUpdater.class);
        publisher = mock(ApplicationEventPublisher.class);
        kafka = mock(OrderUpdateProducer.class);
        slots = mock(TakeawaySlotService.class);
        ReflectionTestUtils.setField(service, "mongoComandReadRepository", reads);
        ReflectionTestUtils.setField(service, "statusUpdater", updater);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
        ReflectionTestUtils.setField(service, "orderUpdateProducer", kafka);
        ReflectionTestUtils.setField(service, "takeawaySlotService", slots);
    }

    private static ComandJpa comand(ComandStatus status, ComandWaiterType type, Boolean approvalRequired) {
        ComandJpa c = new ComandJpa();
        c.setId("cmd_1");
        c.setIdAgency(7L);
        c.setStatus(status);
        c.setComandWaiterType(type);
        c.setApprovalRequired(approvalRequired);
        c.setTime("2026-10-09T20:00");
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    @Test
    void paidTakeawayBecomesPendingAndIsCreatedOnlyOnce() {
        when(reads.findById("cmd_1"))
                .thenReturn(Optional.of(comand(ComandStatus.AWAIT_PAYMENT, ComandWaiterType.TAKE_AWAY, null)))
                .thenReturn(Optional.of(comand(ComandStatus.PENDING, ComandWaiterType.TAKE_AWAY, null)));
        when(updater.compareAndSet(eq("cmd_1"), isNull(), eq(List.of(ComandStatus.AWAIT_PAYMENT)),
                eq(ComandStatus.PENDING), any())).thenReturn(true);

        assertTrue(service.onPaymentCompleted("cmd_1"));
        assertFalse(service.onPaymentCompleted("cmd_1")); // retry del webhook

        verify(publisher, times(1)).publishEvent(new ComandCreatedEvent("cmd_1", "7"));
        verify(kafka, times(1)).sendUpdate(eq(7L), contains("\"status\":\"PENDING\""));
    }

    @Test
    void concurrentTransitionLosingTheRaceDoesNotNotify() {
        when(reads.findById("cmd_1"))
                .thenReturn(Optional.of(comand(ComandStatus.AWAIT_PAYMENT, ComandWaiterType.TABLE, null)));
        when(updater.compareAndSet(any(), any(), any(), any(), any())).thenReturn(false);

        assertFalse(service.onPaymentCompleted("cmd_1"));
        verifyNoInteractions(publisher, kafka);
    }

    @Test
    void paidTableComandGoesToAwait() {
        when(reads.findById("cmd_1"))
                .thenReturn(Optional.of(comand(ComandStatus.AWAIT_PAYMENT, ComandWaiterType.TABLE, null)));
        when(updater.compareAndSet(eq("cmd_1"), isNull(), any(), eq(ComandStatus.AWAIT), any())).thenReturn(true);

        assertTrue(service.onPaymentCompleted("cmd_1"));
        verify(publisher).publishEvent(new ComandCreatedEvent("cmd_1", "7"));
    }

    @Test
    void authorizedReserveOrderAwaitsApprovalWithoutPrinting() {
        when(reads.findById("cmd_1"))
                .thenReturn(Optional.of(comand(ComandStatus.AWAIT_PAYMENT, ComandWaiterType.TAKE_AWAY, true)))
                .thenReturn(Optional.of(comand(ComandStatus.AWAIT_APPROVAL, ComandWaiterType.TAKE_AWAY, true)));
        when(updater.compareAndSet(eq("cmd_1"), isNull(), eq(List.of(ComandStatus.AWAIT_PAYMENT)),
                eq(ComandStatus.AWAIT_APPROVAL), any())).thenReturn(true);

        assertTrue(service.onPaymentAuthorized("cmd_1", "pi_1"));
        assertFalse(service.onPaymentAuthorized("cmd_1", "pi_1")); // evento duplicato

        verify(publisher, never()).publishEvent(any(ComandCreatedEvent.class));
        verify(kafka, times(1)).sendUpdate(eq(7L), contains("\"status\":\"AWAIT_APPROVAL\""));
    }
}
