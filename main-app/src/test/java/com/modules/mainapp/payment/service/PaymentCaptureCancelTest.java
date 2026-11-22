package com.modules.mainapp.payment.service;

import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.PaymentAuthorizationVoidedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.modules.mainapp.payment.sumup.SumUpCredentials;
import com.modules.ordermodule.model.ComandJpa;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Flusso "commissioni minime": autorizzazione carta alla conferma, incasso alla presa in carico (o dal job),
 * annullamento gratuito prima dell'incasso, rimborso (commissione persa) solo dopo. Gateway Stripe/SumUp mockati.
 */
class PaymentCaptureCancelTest {

    private PaymentTestSupport t;
    private final StripeCredentials creds = new StripeCredentials(7, "sk_test_x", "acct_own");
    private final AuthenticatedUserProvider auth = mock(AuthenticatedUserProvider.class);

    @BeforeEach
    void setUp() {
        t = new PaymentTestSupport();
        ReflectionTestUtils.setField(t.service, "authUserProvider", auth);
        when(auth.getAgencyId()).thenReturn(7L);
        when(t.accounts.stripeCredentials(7L)).thenReturn(Optional.of(creds));
    }

    private static PaymentIntent pi(String status) {
        PaymentIntent pi = new PaymentIntent();
        pi.setId("pi_1");
        pi.setStatus(status);
        return pi;
    }

    private static ComandJpa comand(ComandStatus status) {
        ComandJpa c = new ComandJpa();
        c.setId("cmd_1");
        c.setIdAgency(7L);
        c.setStatus(status);
        return c;
    }

    private void comandIs(ComandStatus status) {
        when(t.comands.findById("cmd_1")).thenReturn(Optional.of(comand(status)));
    }

    // ── Creazione: carta solo autorizzata ───────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void normalOrderHoldsOnlyCards() {
        PaymentIntentCreateParams params = StripePaymentProvider.buildParams(new OnlinePaymentProvider.IntentRequest(
                7, "pizzeria", "Pizzeria", "cmd_1", null, 1500, "eur", false, 0));
        Map<String, Object> map = params.toMap();
        assertNull(map.get("capture_method"), "gli altri metodi restano a capture automatica");
        Map<String, Object> card = (Map<String, Object>) ((Map<String, Object>) map.get("payment_method_options")).get("card");
        assertEquals("manual", card.get("capture_method"));
    }

    // ── Incasso alla presa in carico ────────────────────────────────────────

    @Test
    void acceptanceCapturesAuthorizationIdempotently() throws Exception {
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", List.of(PaymentService.STATUS_AUTHORIZED)))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        when(t.stripe.capturePaymentIntent(creds, "pi_1", "capture-pi_1")).thenReturn(pi("succeeded"));

        assertTrue(t.service.captureOnAcceptance("cmd_1"));
        assertTrue(t.service.captureOnAcceptance("cmd_1")); // doppio click / evento ripetuto: stessa idempotency key
        verify(t.stripe, times(2)).capturePaymentIntent(creds, "pi_1", "capture-pi_1");
    }

    @Test
    void acceptanceWithoutAuthorizationDoesNothing() throws Exception {
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        assertTrue(t.service.captureOnAcceptance("cmd_1"));
        verifyNoInteractions(t.stripe);
    }

    @Test
    void captureWebhookCompletesAuthorizedPayment() {
        PaymentJpa p = stripePayment(PaymentService.STATUS_AUTHORIZED);
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(p));
        when(t.repo.updateStatusIfIn(eq(5L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any()))
                .thenReturn(1).thenReturn(0);
        String json = "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"amount\":1500,\"status\":\"succeeded\"}";

        t.service.processStripeEvent(7, event("payment_intent.succeeded", json));
        t.service.processStripeEvent(7, event("payment_intent.succeeded", json)); // retry Stripe

        verify(t.publisher, times(1)).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "pi_1"));
    }

    // ── Annulla pagamento (gratis) ──────────────────────────────────────────

    @Test
    void cancelAuthorizationVoidsWithoutFeesOnce() throws Exception {
        PaymentJpa p = stripePayment(PaymentService.STATUS_AUTHORIZED);
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.of(p));
        comandIs(ComandStatus.PENDING);
        when(t.stripe.cancelPaymentIntent(creds, "pi_1")).thenReturn(pi("canceled"));
        when(t.repo.updateStatusIfIn(eq(5L), eq(List.of(PaymentService.STATUS_AUTHORIZED)), eq(PaymentService.STATUS_CANCELED), any()))
                .thenReturn(1);

        Map<String, Object> res = t.service.cancelAuthorization(5L);

        assertEquals(PaymentService.STATUS_CANCELED, res.get("status"));
        verify(t.stripe, never()).createRefund(any(), any(), any());
        verify(t.publisher, times(1)).publishEvent(new PaymentAuthorizationVoidedEvent("cmd_1", "7", 1500, "pi_1"));

        // Webhook payment_intent.canceled che arriva dopo: nessun secondo evento
        when(t.repo.findByStripePaymentIntentId("pi_1")).thenReturn(Optional.of(p));
        when(t.repo.updateStatusIfIn(eq(5L), eq(List.of(PaymentService.STATUS_AUTHORIZED)), eq(PaymentService.STATUS_CANCELED), any()))
                .thenReturn(0);
        t.service.processStripeEvent(7, event("payment_intent.canceled",
                "{\"id\":\"pi_1\",\"object\":\"payment_intent\",\"status\":\"canceled\"}"));
        verify(t.publisher, times(1)).publishEvent(any(PaymentAuthorizationVoidedEvent.class));
    }

    @Test
    void cancelOfCapturedPaymentIs409AndTouchesNothing() {
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.of(stripePayment(PaymentService.STATUS_COMPLETED)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> t.service.cancelAuthorization(5L));
        assertEquals(409, e.getStatusCode().value());
        verifyNoInteractions(t.stripe);
    }

    @Test
    void cancelOfOrderAwaitingApprovalIs409() {
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        comandIs(ComandStatus.AWAIT_APPROVAL);
        assertThrows(ResponseStatusException.class, () -> t.service.cancelAuthorization(5L));
        verifyNoInteractions(t.stripe);
    }

    @Test
    void cancelRefusedByStripeIs409() throws Exception {
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        comandIs(ComandStatus.PROGRESS);
        when(t.stripe.cancelPaymentIntent(creds, "pi_1")).thenReturn(pi("succeeded")); // già incassato
        assertThrows(ResponseStatusException.class, () -> t.service.cancelAuthorization(5L));
        verify(t.publisher, never()).publishEvent(any(PaymentAuthorizationVoidedEvent.class));
    }

    @Test
    void otherAgencyPaymentIs404() {
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> t.service.cancelAuthorization(5L));
        assertEquals(404, e.getStatusCode().value());
    }

    // ── Rimborso solo dopo l'incasso ────────────────────────────────────────

    @Test
    void refundOfUncapturedAuthorizationIsRefused() throws Exception {
        when(t.repo.findByIdAndIdAgency(5L, 7L)).thenReturn(Optional.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> t.service.refund(5L, null));
        assertEquals(409, e.getStatusCode().value());
        verify(t.stripe, never()).createRefund(any(), any(), any());
    }

    @Test
    void sumUpUpfrontChargeCannotBeVoidedOnlyRefunded() {
        PaymentJpa p = sumupPayment(PaymentService.STATUS_AUTHORIZED, true);
        p.setCapturedUpfront(true);
        when(t.repo.findByIdAndIdAgency(9L, 7L)).thenReturn(Optional.of(p));
        assertThrows(ResponseStatusException.class, () -> t.service.cancelAuthorization(9L));
        verifyNoInteractions(t.sumup);
    }

    // ── Job di sicurezza (prima della scadenza dell'autorizzazione) ─────────

    @Test
    void staleAuthorizationOfActiveOrderIsCaptured() throws Exception {
        when(t.repo.findTop200ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(eq(PaymentService.STATUS_AUTHORIZED), any()))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        comandIs(ComandStatus.AWAIT);
        when(t.stripe.capturePaymentIntent(creds, "pi_1", "capture-pi_1")).thenReturn(pi("succeeded"));

        assertEquals(1, t.service.settleStaleAuthorizations(LocalDateTime.now()));
        verify(t.stripe, never()).cancelPaymentIntent(any(), any());
    }

    @Test
    void staleAuthorizationOfDeletedOrderIsVoided() throws Exception {
        when(t.repo.findTop200ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(eq(PaymentService.STATUS_AUTHORIZED), any()))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        comandIs(ComandStatus.DELETED);
        when(t.stripe.cancelPaymentIntent(creds, "pi_1")).thenReturn(pi("canceled"));

        assertEquals(1, t.service.settleStaleAuthorizations(LocalDateTime.now()));
        verify(t.stripe, never()).capturePaymentIntent(any(), any(), any());
    }

    @Test
    void staleAuthorizationAwaitingApprovalIsLeftToApprovalFlow() {
        when(t.repo.findTop200ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(eq(PaymentService.STATUS_AUTHORIZED), any()))
                .thenReturn(List.of(stripePayment(PaymentService.STATUS_AUTHORIZED)));
        comandIs(ComandStatus.AWAIT_APPROVAL);

        assertEquals(0, t.service.settleStaleAuthorizations(LocalDateTime.now()));
        verifyNoInteractions(t.stripe);
    }

    @Test
    void jobLooksOnlyAtAuthorizationsOlderThanTheThreshold() {
        when(t.repo.findTop200ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(any(), any())).thenReturn(List.of());
        LocalDateTime now = LocalDateTime.of(2026, 11, 22, 20, 0);
        t.service.settleStaleAuthorizations(now);
        verify(t.repo).findTop200ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
                PaymentService.STATUS_AUTHORIZED, now.minus(PaymentService.AUTO_CAPTURE_AFTER));
    }

    @Test
    void sumUpNormalOrderIsChargedImmediately() {
        // SumUp non ha pre-autorizzazione: un ordine normale viene addebitato subito (COMPLETED), mai AUTHORIZED
        PaymentJpa p = sumupPayment(PaymentService.STATUS_PENDING, false);
        when(t.accounts.sumupCredentials(7L)).thenReturn(Optional.of(new SumUpCredentials(7, "k", "MC123")));
        when(t.repo.updateStatusIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);
        t.sumupProvider.apply(p, new com.modules.mainapp.payment.sumup.SumUpCheckout("co_1", "ref", "PAID",
                new java.math.BigDecimal("15.00"), "EUR", "MC123", null, List.of()));
        verify(t.repo, never()).authorizeIfIn(anyLong(), any(), any(), anyBoolean(), any());
        verify(t.publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "co_1"));
    }
}
