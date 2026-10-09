package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.PaymentAuthorizedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.PaymentRefundedEvent;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.repository.PaymentRepository;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.ApiResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Capture manuale (ordini "su richiesta"): webhook amount_capturable_updated, capture, rimborso totale. */
class PaymentServiceAuthorizationTest {

    private PaymentService service;
    private PaymentRepository repo;
    private ApplicationEventPublisher publisher;
    private StripeGateway stripe;

    @BeforeEach
    void setUp() {
        service = new PaymentService();
        repo = mock(PaymentRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        stripe = mock(StripeGateway.class);
        ReflectionTestUtils.setField(service, "paymentRepository", repo);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
        ReflectionTestUtils.setField(service, "stripe", stripe);
    }

    private static Event event(String type, String objectJson) {
        String json = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION + "\","
                + "\"type\":\"" + type + "\",\"data\":{\"object\":" + objectJson + "}}";
        return ApiResource.GSON.fromJson(json, Event.class);
    }

    private static PaymentJpa payment(String status) {
        PaymentJpa p = new PaymentJpa();
        p.setIdAgency(7);
        p.setComandId("cmd_1");
        p.setAmountCents(1500);
        p.setStripePaymentIntentId("pi_1");
        p.setStatus(status);
        return p;
    }

    @Test
    void amountCapturableUpdatedPublishesAuthorizedOnce() {
        Event e = event("payment_intent.amount_capturable_updated",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500,\"amount_capturable\":1500,"
                        + "\"capture_method\":\"manual\",\"status\":\"requires_capture\"}");
        when(repo.updateStatusIfIn(eq("pi_1"), eq(List.of(PaymentService.STATUS_PENDING, PaymentService.STATUS_FAILED)),
                eq(PaymentService.STATUS_AUTHORIZED), any())).thenReturn(1).thenReturn(0);
        when(repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment(PaymentService.STATUS_AUTHORIZED)));

        service.processEvent(e);
        service.processEvent(e); // retry Stripe

        verify(publisher, times(1)).publishEvent(new PaymentAuthorizedEvent("cmd_1", "7", 1500, "pi_1"));
        verify(publisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    void amountCapturableZeroIsIgnored() {
        service.processEvent(event("payment_intent.amount_capturable_updated",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500,\"amount_capturable\":0}"));
        verify(repo, never()).updateStatusIfIn(any(), any(), any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void succeededAfterCaptureCompletesAuthorizedPayment() {
        when(repo.updateStatusIfIn(eq("pi_1"), argThat(l -> l.contains(PaymentService.STATUS_AUTHORIZED)),
                eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);
        when(repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(payment(PaymentService.STATUS_COMPLETED)));

        service.processEvent(event("payment_intent.succeeded",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500}"));

        verify(publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    @Test
    void captureUsesIdempotencyKeyPerIntent() throws Exception {
        when(repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", List.of(PaymentService.STATUS_AUTHORIZED)))
                .thenReturn(List.of(payment(PaymentService.STATUS_AUTHORIZED)));
        PaymentIntent captured = new PaymentIntent();
        captured.setStatus("succeeded");
        when(stripe.capturePaymentIntent("pi_1", "capture-pi_1")).thenReturn(captured);

        assertTrue(service.captureAuthorized("cmd_1"));
        verify(stripe).capturePaymentIntent("pi_1", "capture-pi_1");
    }

    @Test
    void captureWithoutAuthorizationFails() {
        when(repo.findByComandIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(repo.existsByComandIdAndStatus("cmd_1", PaymentService.STATUS_COMPLETED)).thenReturn(false);
        assertFalse(service.captureAuthorized("cmd_1"));
    }

    @Test
    void fullRefundPublishesFullFlag() {
        PaymentJpa p = payment(PaymentService.STATUS_COMPLETED);
        when(repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(p));

        service.processEvent(event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":500,\"refunded\":false}"));
        verify(publisher).publishEvent(new PaymentRefundedEvent("cmd_1", "7", 500, false));

        service.processEvent(event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":1500,\"refunded\":true}"));
        verify(publisher).publishEvent(new PaymentRefundedEvent("cmd_1", "7", 1500, true));
    }
}
