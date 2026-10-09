package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.modules.ordermodule.model.ComandJpa;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentTestSupport.stripePayment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Stripe sull'account del locale: nessuna destination charge / commissione piattaforma. */
class StripePaymentProviderTest {

    private PaymentTestSupport t;
    private final StripeCredentials creds = new StripeCredentials(7, "sk_test_own", "acct_own");

    @BeforeEach
    void setUp() {
        t = new PaymentTestSupport();
        when(t.accounts.stripeCredentials(7L)).thenReturn(Optional.of(creds));
        when(t.accounts.enabledProvider(7L)).thenReturn(PaymentProvider.STRIPE);
        AgencyJpa agency = new AgencyJpa("Pizzeria", "pizzeria");
        when(t.accounts.requireAgency(7L)).thenReturn(agency);
        AgencyPaymentAccountJpa account = new AgencyPaymentAccountJpa();
        account.setIdAgency(7L);
        account.setStripePublishableKey("pk_test_own");
        when(t.accounts.findAccount(7L)).thenReturn(Optional.of(account));
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
    }

    private void comand(boolean approvalRequired) {
        ComandJpa c = new ComandJpa();
        c.setId("cmd_1");
        c.setIdAgency(7L);
        c.setStatus(ComandStatus.AWAIT_PAYMENT);
        c.setApprovalRequired(approvalRequired);
        c.setCreatedAt(LocalDateTime.now());
        when(t.comands.findById("cmd_1")).thenReturn(Optional.of(c));
    }

    private static PaymentIntent intent() {
        PaymentIntent pi = new PaymentIntent();
        pi.setId("pi_new");
        pi.setClientSecret("pi_new_secret");
        return pi;
    }

    @Test
    void intentIsAPlainChargeOnTheRestaurantAccount() throws Exception {
        comand(false);
        when(t.stripe.createPaymentIntent(eq(creds), any(), anyString())).thenReturn(intent());

        PaymentIntentResponse resp = t.service.createIntent(7L, "pizzeria", null, "cmd_1", "eur");

        ArgumentCaptor<PaymentIntentCreateParams> params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(t.stripe).createPaymentIntent(eq(creds), params.capture(), key.capture());
        Map<String, Object> map = params.getValue().toMap();
        assertFalse(map.containsKey("transfer_data"));
        assertFalse(map.containsKey("on_behalf_of"));
        assertFalse(map.containsKey("application_fee_amount"));
        assertFalse(map.containsKey("capture_method"));
        assertEquals(1500L, map.get("amount"));
        assertTrue(key.getValue().contains("acct_own"), "idempotency key legata all'account del locale");

        PaymentIntentResponse.Stripe s = assertInstanceOf(PaymentIntentResponse.Stripe.class, resp);
        assertEquals("STRIPE", s.provider());
        assertEquals("pi_new_secret", s.clientSecret());
        assertEquals("pk_test_own", s.publishableKey());
        assertFalse(s.manualCapture());

        ArgumentCaptor<PaymentJpa> saved = ArgumentCaptor.forClass(PaymentJpa.class);
        verify(t.repo).save(saved.capture());
        assertEquals(PaymentProvider.STRIPE, saved.getValue().getProvider());
        assertEquals("acct_own", saved.getValue().getStripeAccountId());
        assertNull(saved.getValue().getApplicationFeeCents());
    }

    @Test
    void reserveOrderUsesManualCapture() throws Exception {
        comand(true);
        when(t.stripe.createPaymentIntent(eq(creds), any(), anyString())).thenReturn(intent());

        PaymentIntentResponse resp = t.service.createIntent(7L, "pizzeria", null, "cmd_1", "eur");

        ArgumentCaptor<PaymentIntentCreateParams> params = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        verify(t.stripe).createPaymentIntent(eq(creds), params.capture(), anyString());
        assertEquals("manual", params.getValue().toMap().get("capture_method"));
        assertTrue(((PaymentIntentResponse.Stripe) resp).manualCapture());
    }

    @Test
    void refundHasNoConnectReversals() throws Exception {
        PaymentJpa p = stripePayment(PaymentService.STATUS_COMPLETED);
        Refund refund = new Refund();
        refund.setId("re_1");
        refund.setStatus("succeeded");
        when(t.stripe.createRefund(eq(creds), any(), anyString())).thenReturn(refund);

        t.stripeProvider.refund(p, 500);

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        verify(t.stripe).createRefund(eq(creds), params.capture(), anyString());
        Map<String, Object> map = params.getValue().toMap();
        assertFalse(map.containsKey("reverse_transfer"));
        assertFalse(map.containsKey("refund_application_fee"));
        assertEquals(500L, map.get("amount"));
    }

    @Test
    void refundOfPaymentFromAnotherStripeAccountIs409() {
        PaymentJpa p = stripePayment(PaymentService.STATUS_COMPLETED);
        p.setStripeAccountId("acct_old_connect");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> t.stripeProvider.refund(p, 500));
        assertEquals(409, e.getStatusCode().value());
        assertEquals(StripePaymentProvider.WRONG_ACCOUNT, e.getReason());
        verifyNoInteractions(t.stripe);
    }
}
