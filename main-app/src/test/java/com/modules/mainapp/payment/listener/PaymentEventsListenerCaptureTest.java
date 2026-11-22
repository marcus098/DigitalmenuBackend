package com.modules.mainapp.payment.listener;

import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.PaymentAuthorizationVoidedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.ordermodule.event.ComandStatusChangedEvent;
import com.modules.ordermodule.kafka.OrderUpdateProducer;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.modules.ordermodule.service.OrderComandService;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Comanda ↔ autorizzazione carta: incasso alla presa in carico, "pagata" tolta se l'autorizzazione viene annullata. */
class PaymentEventsListenerCaptureTest {

    private MongoTemplate mongo;
    private MongoComandRepository comands;
    private OrderUpdateProducer kafka;
    private PaymentService payments;
    private PaymentEventsListener listener;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        comands = mock(MongoComandRepository.class);
        kafka = mock(OrderUpdateProducer.class);
        payments = mock(PaymentService.class);
        listener = new PaymentEventsListener(mongo, comands, kafka, payments, mock(OrderComandService.class));
        ComandJpa c = new ComandJpa();
        c.setId("cmd_1");
        c.setIdAgency(7L);
        c.setStatus(ComandStatus.PENDING);
        when(comands.findById("cmd_1")).thenReturn(Optional.of(c));
    }

    private static UpdateResult modified(long n) {
        return UpdateResult.acknowledged(n, n, null);
    }

    @Test
    void progressAndCompletedCaptureOtherStatusesDoNot() {
        for (ComandStatus s : ComandStatus.values()) {
            listener.onComandAccepted(new ComandStatusChangedEvent("cmd_1", "7", ComandStatus.PENDING, s));
        }
        verify(payments, times(2)).captureOnAcceptance("cmd_1");
    }

    @Test
    void captureFailureDoesNotPropagate() {
        when(payments.captureOnAcceptance("cmd_1")).thenThrow(new RuntimeException("stripe down"));
        assertDoesNotThrow(() -> listener.onComandAccepted(
                new ComandStatusChangedEvent("cmd_1", "7", ComandStatus.PENDING, ComandStatus.PROGRESS)));
    }

    @Test
    void voidedAuthorizationUnmarksGuaranteedComandOnce() {
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(PaymentEventsListener.COMAND_COLLECTION)))
                .thenReturn(modified(1)).thenReturn(modified(0));
        PaymentAuthorizationVoidedEvent e = new PaymentAuthorizationVoidedEvent("cmd_1", "7", 1500, "pi_1");

        listener.onAuthorizationVoided(e);
        listener.onAuthorizationVoided(e);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(q.capture(), u.capture(), eq(PaymentEventsListener.COMAND_COLLECTION));
        // Solo comande "garantite" (paid + paymentAuthorized): gli ordini su richiesta rifiutati non vengono toccati
        assertEquals(Boolean.TRUE, q.getValue().getQueryObject().get("paymentAuthorized"));
        assertEquals(Boolean.TRUE, q.getValue().getQueryObject().get("paid"));
        assertEquals(false, ((org.bson.Document) u.getValue().getUpdateObject().get("$set")).get("paid"));
        verify(kafka, times(1)).sendUpdate(eq(7L), contains("\"paid\":false"));
    }

    @Test
    void captureOfGuaranteedComandClearsAuthorizedFlag() {
        // 1° update (paid != true) non modifica nulla: la comanda era già "pagata" dall'autorizzazione
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(PaymentEventsListener.COMAND_COLLECTION)))
                .thenReturn(modified(0)).thenReturn(modified(1));

        listener.onPaymentCompleted(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));

        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(mongo, times(2)).updateFirst(any(Query.class), u.capture(), eq(PaymentEventsListener.COMAND_COLLECTION));
        List<Update> updates = u.getAllValues();
        assertEquals(false, ((org.bson.Document) updates.get(1).getUpdateObject().get("$set")).get("paymentAuthorized"));
        verify(kafka).sendUpdate(eq(7L), contains("\"paid\":true"));
    }
}
