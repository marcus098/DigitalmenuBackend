package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.mainapp.payment.ComandTotalCalculator;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.ordermodule.repository.MongoComandRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Idempotenza del webhook e guardie di createIntent, senza Spring/DB/Stripe. */
class PaymentServiceWebhookTest {

    private PaymentService service;
    private PaymentRepository repo;
    private ApplicationEventPublisher publisher;
    private StripeGateway stripe;
    private StripeConnectService connect;
    private MongoComandRepository comands;

    @BeforeEach
    void setUp() {
        service = new PaymentService();
        repo = mock(PaymentRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        stripe = mock(StripeGateway.class);
        connect = mock(StripeConnectService.class);
        comands = mock(MongoComandRepository.class);
        ReflectionTestUtils.setField(service, "paymentRepository", repo);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
        ReflectionTestUtils.setField(service, "stripe", stripe);
        ReflectionTestUtils.setField(service, "connectService", connect);
        ReflectionTestUtils.setField(service, "mongoComandRepository", comands);
        ReflectionTestUtils.setField(service, "comandTotalCalculator", (ComandTotalCalculator) id -> 1500L);
    }

    private static Event event(String type, String objectJson) {
        String json = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION + "\","
                + "\"type\":\"" + type + "\",\"data\":{\"object\":" + objectJson + "}}";
        return ApiResource.GSON.fromJson(json, Event.class);
    }

    private static PaymentJpa payment(long amount, String status) {
        PaymentJpa p = new PaymentJpa();
        p.setIdAgency(7);
        p.setComandId("cmd_1");
        p.setAmountCents(amount);
        p.setStripePaymentIntentId("pi_1");
        p.setStatus(status);
        return p;
    }

    @Test
    void succeededPublishesOnlyOnFirstDelivery() {
        Event e = event("payment_intent.succeeded", "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500}");
        when(repo.updateStatusIfIn(eq("pi_1"), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any()))
                .thenReturn(1).thenReturn(0);
        when(repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment(1500, "COMPLETED")));

        service.processEvent(e);
        service.processEvent(e); // retry Stripe

        verify(publisher, times(1)).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    @Test
    void refundEventsAreCumulativeAndNeverRegress() {
        PaymentJpa p = payment(1500, PaymentService.STATUS_COMPLETED);
        when(repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(p));

        service.processEvent(event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":1500,\"refunded\":true}"));
        assertEquals(PaymentService.STATUS_REFUNDED, p.getStatus());
        assertEquals(1500L, p.getRefundedCents());

        // evento più vecchio (rimborso parziale) consegnato in ritardo
        service.processEvent(event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":500,\"refunded\":false}"));
        assertEquals(PaymentService.STATUS_REFUNDED, p.getStatus());
        assertEquals(1500L, p.getRefundedCents());
    }

    @Test
    void createIntentRejectsRestaurantWithoutConnectedAccount() {
        ComandJpa comand = new ComandJpa();
        comand.setId("cmd_1");
        comand.setIdAgency(7L);
        when(stripe.isConfigured()).thenReturn(true);
        when(comands.findById("cmd_1")).thenReturn(Optional.of(comand));
        when(connect.requireAgency(7L)).thenReturn(new AgencyJpa());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createIntent(7L, null, "cmd_1", "eur"));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(PaymentService.PAYMENTS_NOT_ACTIVE, ex.getReason());
        verifyNoInteractions(publisher);
    }
}
