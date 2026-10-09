package com.modules.mainapp.payment.service;

import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.ordermodule.model.ComandJpa;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Webhook Stripe per locale: firma con il secret del locale, idempotenza, isolamento tra locali. */
class PaymentServiceWebhookTest {

    private PaymentTestSupport t;

    @BeforeEach
    void setUp() {
        t = new PaymentTestSupport();
    }

    private static final String SUCCEEDED_JSON = "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500}";

    @Test
    void succeededPublishesOnlyOnFirstDelivery() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("PENDING")));
        when(t.repo.updateStatusIfIn(eq(5L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any()))
                .thenReturn(1).thenReturn(0);

        t.service.processStripeEvent(7, event("payment_intent.succeeded", SUCCEEDED_JSON));
        t.service.processStripeEvent(7, event("payment_intent.succeeded", SUCCEEDED_JSON)); // retry Stripe

        verify(t.publisher, times(1)).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    @Test
    void eventFromAnotherAgencyAccountIsIgnored() {
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("PENDING")));

        t.service.processStripeEvent(8, event("payment_intent.succeeded", SUCCEEDED_JSON));

        verify(t.repo, never()).updateStatusIfIn(anyLong(), any(), any(), any());
        verifyNoInteractions(t.publisher);
    }

    @Test
    void refundEventsAreCumulativeAndNeverRegress() {
        PaymentJpa p = stripePayment(PaymentService.STATUS_COMPLETED);
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(p));

        t.service.processStripeEvent(7, event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":1500,\"refunded\":true}"));
        assertEquals(PaymentService.STATUS_REFUNDED, p.getStatus());
        assertEquals(1500L, p.getRefundedCents());

        // evento più vecchio (rimborso parziale) consegnato in ritardo
        t.service.processStripeEvent(7, event("charge.refunded",
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_intent\":\"pi_1\",\"amount\":1500,\"amount_refunded\":500,\"refunded\":false}"));
        assertEquals(PaymentService.STATUS_REFUNDED, p.getStatus());
        assertEquals(1500L, p.getRefundedCents());
    }

    // ── Firma con il webhook secret DEL LOCALE ──

    private static String sign(String payload, String secret) throws Exception {
        long ts = Webhook.Util.getTimeNow();
        return "t=" + ts + ",v1=" + Webhook.Util.computeHmacSha256(secret, ts + "." + payload);
    }

    private void withRealSignatureVerification() {
        ReflectionTestUtils.setField(t.service, "stripe", new StripeGateway());
        AgencyPaymentAccountJpa account = new AgencyPaymentAccountJpa();
        account.setIdAgency(7L);
        account.setWebhookToken("tok_7");
        when(t.accounts.findByWebhookToken("tok_7")).thenReturn(Optional.of(account));
        when(t.accounts.stripeWebhookSecret(account)).thenReturn(Optional.of("whsec_agency7"));
    }

    @Test
    void webhookSignedWithAgencySecretIsProcessed() throws Exception {
        withRealSignatureVerification();
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(stripePayment("PENDING")));
        when(t.repo.updateStatusIfIn(eq(5L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + com.stripe.Stripe.API_VERSION
                + "\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":" + SUCCEEDED_JSON + "}}";

        t.service.handleStripeWebhook("tok_7", payload, sign(payload, "whsec_agency7"));

        verify(t.publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    @Test
    void webhookSignedWithAnotherSecretIsRejected() throws Exception {
        withRealSignatureVerification();
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":" + SUCCEEDED_JSON + "}}";

        assertThrows(SignatureVerificationException.class,
                () -> t.service.handleStripeWebhook("tok_7", payload, sign(payload, "whsec_other_agency")));
        assertThrows(SignatureVerificationException.class,
                () -> t.service.handleStripeWebhook("unknown", payload, sign(payload, "whsec_agency7")));
        verifyNoInteractions(t.publisher);
    }

    @Test
    void createIntentRejectsRestaurantWithoutActiveProvider() {
        ComandJpa comand = new ComandJpa();
        comand.setId("cmd_1");
        comand.setIdAgency(7L);
        when(t.comands.findById("cmd_1")).thenReturn(Optional.of(comand));
        when(t.accounts.enabledProvider(7L)).thenReturn(PaymentProvider.NONE);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> t.service.createIntent(7L, "pizzeria", null, "cmd_1", "eur"));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals(PaymentService.PAYMENTS_NOT_ACTIVE, ex.getReason());
        verifyNoInteractions(t.publisher, t.stripe, t.sumup);
    }
}
