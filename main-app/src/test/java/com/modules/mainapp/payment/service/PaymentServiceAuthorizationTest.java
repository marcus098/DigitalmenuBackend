package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.PaymentAuthorizedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.PaymentRefundedEvent;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Stripe, capture manuale (ordini "su richiesta"): webhook amount_capturable_updated, capture, rimborso totale. */
class PaymentServiceAuthorizationTest {

    private PaymentTestSupport t;
    private final StripeCredentials creds = new StripeCredentials(7, "sk_test_x", "acct_own");

    @BeforeEach
    void setUp() {
        t = new PaymentTestSupport();
        when(t.accounts.stripeCredentials(7L)).thenReturn(Optional.of(creds));
    }

    @Test
    void amountCapturableUpdatedPublishesAuthorizedOnce() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("PENDING")));
        when(t.repo.authorizeIfIn(eq(5L), eq(List.of(PaymentService.STATUS_PENDING, PaymentService.STATUS_FAILED)),
                eq(PaymentService.STATUS_AUTHORIZED), eq(false), any())).thenReturn(1).thenReturn(0);
        String json = "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500,\"amount_capturable\":1500,"
                + "\"capture_method\":\"manual\",\"status\":\"requires_capture\"}";

        t.service.processStripeEvent(7, event("payment_intent.amount_capturable_updated", json));
        t.service.processStripeEvent(7, event("payment_intent.amount_capturable_updated", json)); // retry Stripe

        verify(t.publisher, times(1)).publishEvent(new PaymentAuthorizedEvent("cmd_1", "7", 1500, "pi_1"));
        verify(t.publisher, never()).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    void amountCapturableZeroIsIgnored() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("PENDING")));
        t.service.processStripeEvent(7, event("payment_intent.amount_capturable_updated",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500,\"amount_capturable\":0}"));
        verify(t.repo, never()).authorizeIfIn(anyLong(), any(), any(), anyBoolean(), any());
        verifyNoInteractions(t.publisher);
    }

    @Test
    void succeededAfterCaptureCompletesAuthorizedPayment() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("AUTHORIZED")));
        when(t.repo.updateStatusIfIn(eq(5L), argThat(l -> l.contains(PaymentService.STATUS_AUTHORIZED)),
                eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);

        t.service.processStripeEvent(7, event("payment_intent.succeeded",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500}"));

        verify(t.publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    @Test
    void captureUsesAgencyCredentialsAndIdempotencyKeyPerIntent() throws Exception {
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", List.of(PaymentService.STATUS_AUTHORIZED)))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        PaymentIntent captured = new PaymentIntent();
        captured.setStatus("succeeded");
        when(t.stripe.capturePaymentIntent(creds, "pi_1", "capture-pi_1")).thenReturn(captured);

        assertTrue(t.service.captureAuthorized("cmd_1"));
        verify(t.stripe).capturePaymentIntent(creds, "pi_1", "capture-pi_1");
    }

    @Test
    void captureWithoutAuthorizationFails() {
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(t.repo.existsByComandIdAndStatus("cmd_1", PaymentService.STATUS_COMPLETED)).thenReturn(false);
        assertFalse(t.service.captureAuthorized("cmd_1"));
    }

    @Test
    void rejectCancelsStripeAuthorization() throws Exception {
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", PaymentService.CANCELABLE_STATUSES))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        PaymentIntent canceled = new PaymentIntent();
        canceled.setStatus("canceled");
        when(t.stripe.cancelPaymentIntent(creds, "pi_1")).thenReturn(canceled);

        assertTrue(t.service.cancelOpenIntents("cmd_1"));
        verify(t.repo).updateStatusIfIn(eq(5L), eq(PaymentService.CANCELABLE_STATUSES), eq(PaymentService.STATUS_CANCELED), any());
    }

    @Test
    void fullRefundPublishesFullFlag() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment(PaymentService.STATUS_COMPLETED)));

        t.service.processStripeEvent(7, event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":500,\"refunded\":false}"));
        verify(t.publisher).publishEvent(new PaymentRefundedEvent("cmd_1", "7", 500, false));

        t.service.processStripeEvent(7, event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":1500,\"refunded\":true}"));
        verify(t.publisher).publishEvent(new PaymentRefundedEvent("cmd_1", "7", 1500, true));
    }
}
