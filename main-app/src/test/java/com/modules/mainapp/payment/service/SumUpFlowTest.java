package com.modules.mainapp.payment.service;

import com.modules.authmodule.model.AgencyJpa;
import com.modules.common.model.enums.ComandStatus;
import com.modules.mainapp.payment.PaymentAuthorizedEvent;
import com.modules.mainapp.payment.PaymentCompletedEvent;
import com.modules.mainapp.payment.PaymentRefundedEvent;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.sumup.SumUpCheckout;
import com.modules.mainapp.payment.sumup.SumUpCredentials;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.mainapp.payment.sumup.SumUpGateway;
import com.modules.ordermodule.model.ComandJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.modules.mainapp.payment.service.PaymentTestSupport.sumupPayment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SumUp: webhook non firmato (si rilegge sempre il checkout), addebito anticipato per gli ordini su approvazione
 * (AUTHORIZED + capturedUpfront → COMPLETED all'approvazione, rimborso al rifiuto).
 */
class SumUpFlowTest {

    private PaymentTestSupport t;
    private final SumUpCredentials creds = new SumUpCredentials(7, "sup_sk_agency7", "MC123");

    @BeforeEach
    void setUp() {
        t = new PaymentTestSupport();
        when(t.accounts.sumupCredentials(7L)).thenReturn(Optional.of(creds));
        AgencyPaymentAccountJpa account = new AgencyPaymentAccountJpa();
        account.setIdAgency(7L);
        account.setWebhookToken("tok_7");
        when(t.accounts.findByWebhookToken("tok_7")).thenReturn(Optional.of(account));
        when(t.accounts.findAccount(7L)).thenReturn(Optional.of(account));
    }

    private static SumUpCheckout checkout(String status) {
        return new SumUpCheckout("co_1", "cmd_1-0-abcdef", status, new BigDecimal("15.00"), "EUR", "MC123", null,
                SumUpCheckout.PAID.equals(status)
                        ? List.of(new SumUpCheckout.Transaction("tx_1", "TCODE", "SUCCESSFUL", new BigDecimal("15.00")))
                        : List.of());
    }

    private static final String WEBHOOK_BODY = "{\"event_type\":\"CHECKOUT_STATUS_CHANGED\",\"id\":\"co_1\"}";

    @Test
    void amountConversionsAreExact() {
        assertEquals("15.05", SumUpGateway.toAmount(1505).toPlainString());
        assertEquals("0.10", SumUpGateway.toAmount(10).toPlainString());
        assertEquals(1505L, SumUpGateway.toCents(new BigDecimal("15.05")));
        assertEquals(30L, SumUpGateway.toCents(new BigDecimal("0.3")));
        assertEquals(1999L, SumUpGateway.toCents(new BigDecimal("19.99")));
    }

    @Test
    void paidCheckoutCompletesPaymentAfterRefetch() throws Exception {
        PaymentJpa p = sumupPayment("PENDING", false);
        when(t.repo.findBySumupCheckoutId("co_1")).thenReturn(Optional.of(p));
        when(t.sumup.getCheckout(creds, "co_1")).thenReturn(checkout("PAID"));
        when(t.repo.updateStatusIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1).thenReturn(0);

        t.service.handleSumUpWebhook("tok_7", WEBHOOK_BODY);
        t.service.handleSumUpWebhook("tok_7", WEBHOOK_BODY); // retry SumUp

        verify(t.sumup, times(2)).getCheckout(creds, "co_1");
        verify(t.repo).setSumupTransactionId(eq(9L), eq("tx_1"), any());
        verify(t.publisher, times(1)).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "co_1"));
        verify(t.repo, never()).authorizeIfIn(anyLong(), any(), any(), anyBoolean(), any());
    }

    @Test
    void unknownTokenOrCheckoutIsIgnoredWithoutCallingSumUp() throws Exception {
        when(t.repo.findBySumupCheckoutId("co_x")).thenReturn(Optional.empty());

        t.service.handleSumUpWebhook("tok_unknown", WEBHOOK_BODY);
        t.service.handleSumUpWebhook("tok_7", "{\"event_type\":\"CHECKOUT_STATUS_CHANGED\",\"id\":\"co_x\"}");
        t.service.handleSumUpWebhook("tok_7", "not json");

        verifyNoInteractions(t.sumup, t.publisher);
    }

    @Test
    void checkoutOfAnotherAgencyIsIgnored() throws Exception {
        PaymentJpa other = sumupPayment("PENDING", false);
        other.setIdAgency(8);
        when(t.repo.findBySumupCheckoutId("co_1")).thenReturn(Optional.of(other));

        t.service.handleSumUpWebhook("tok_7", WEBHOOK_BODY);

        verifyNoInteractions(t.sumup, t.publisher);
    }

    @Test
    void transientSumUpErrorIsPropagatedForRetry() throws Exception {
        when(t.repo.findBySumupCheckoutId("co_1")).thenReturn(Optional.of(sumupPayment("PENDING", false)));
        when(t.sumup.getCheckout(creds, "co_1")).thenThrow(new SumUpException(503, "down"));

        assertThrows(SumUpException.class, () -> t.service.handleSumUpWebhook("tok_7", WEBHOOK_BODY));
    }

    @Test
    void approvalRequiredPaidBecomesAuthorizedThenCaptureCompletesWithoutApiCalls() throws Exception {
        PaymentJpa p = sumupPayment("PENDING", true);
        when(t.repo.findBySumupCheckoutId("co_1")).thenReturn(Optional.of(p));
        when(t.sumup.getCheckout(creds, "co_1")).thenReturn(checkout("PAID"));
        when(t.repo.authorizeIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_AUTHORIZED), eq(true), any())).thenReturn(1);

        assertEquals("PAID", t.service.syncSumUpCheckout(7, "co_1").get("status"));

        verify(t.publisher).publishEvent(new PaymentAuthorizedEvent("cmd_1", "7", 1500, "co_1"));
        verify(t.publisher, never()).publishEvent(any(PaymentCompletedEvent.class));

        // Il locale approva: nessuna chiamata a SumUp, COMPLETED + PaymentCompletedEvent
        PaymentJpa authorized = sumupPayment(PaymentService.STATUS_AUTHORIZED, true);
        authorized.setCapturedUpfront(true);
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", List.of(PaymentService.STATUS_AUTHORIZED)))
                .thenReturn(List.of(authorized));
        when(t.repo.updateStatusIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);
        clearInvocations(t.sumup);

        assertTrue(t.service.captureAuthorized("cmd_1"));

        verifyNoInteractions(t.sumup);
        verify(t.publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "co_1"));
    }

    @Test
    void rejectedUpfrontPaymentIsFullyRefunded() throws Exception {
        PaymentJpa authorized = sumupPayment(PaymentService.STATUS_AUTHORIZED, true);
        authorized.setCapturedUpfront(true);
        authorized.setSumupTransactionId("tx_1");
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", PaymentService.CANCELABLE_STATUSES))
                .thenReturn(List.of(authorized));

        assertTrue(t.service.cancelOpenIntents("cmd_1"));

        verify(t.sumup).refund(creds, "tx_1", 1500);
        assertEquals(PaymentService.STATUS_REFUNDED, authorized.getStatus());
        assertEquals(1500L, authorized.getRefundedCents());
        verify(t.repo).save(authorized);
        verify(t.publisher).publishEvent(new PaymentRefundedEvent("cmd_1", "7", 1500, true));
    }

    @Test
    void failedRefundOfUpfrontPaymentReturnsFalse() throws Exception {
        PaymentJpa authorized = sumupPayment(PaymentService.STATUS_AUTHORIZED, true);
        authorized.setCapturedUpfront(true);
        authorized.setSumupTransactionId("tx_1");
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", PaymentService.CANCELABLE_STATUSES))
                .thenReturn(List.of(authorized));
        doThrow(new SumUpException(500, "boom")).when(t.sumup).refund(creds, "tx_1", 1500);

        assertFalse(t.service.cancelOpenIntents("cmd_1"));
        assertEquals(PaymentService.STATUS_AUTHORIZED, authorized.getStatus());
        verifyNoInteractions(t.publisher);
    }

    @Test
    void cancelOfPendingCheckoutThatIsActuallyPaidIsRefusedAndRecorded() throws Exception {
        PaymentJpa p = sumupPayment("PENDING", false);
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", PaymentService.CANCELABLE_STATUSES))
                .thenReturn(List.of(p));
        when(t.sumup.getCheckout(creds, "co_1")).thenReturn(checkout("PAID"));
        when(t.repo.updateStatusIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_COMPLETED), any())).thenReturn(1);

        assertFalse(t.service.cancelOpenIntents("cmd_1"));
        verify(t.sumup, never()).deactivateCheckout(any(), any());
        verify(t.publisher).publishEvent(new PaymentCompletedEvent("cmd_1", "7", 1500, "co_1"));
    }

    @Test
    void cancelOfPendingCheckoutDeactivatesIt() throws Exception {
        PaymentJpa p = sumupPayment("PENDING", false);
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc("cmd_1", PaymentService.CANCELABLE_STATUSES))
                .thenReturn(List.of(p));
        when(t.sumup.getCheckout(creds, "co_1")).thenReturn(checkout("PENDING"));

        assertTrue(t.service.cancelOpenIntents("cmd_1"));
        verify(t.sumup).deactivateCheckout(creds, "co_1");
        verify(t.repo).updateStatusIfIn(eq(9L), anyCollection(), eq(PaymentService.STATUS_CANCELED), any());
    }

    @Test
    void createCheckoutUsesLocalnameRedirectAndTokenReturnUrl() throws Exception {
        ComandJpa c = new ComandJpa();
        c.setId("cmd_1");
        c.setIdAgency(7L);
        c.setStatus(ComandStatus.AWAIT_PAYMENT);
        c.setApprovalRequired(true);
        c.setCreatedAt(LocalDateTime.now());
        when(t.comands.findById("cmd_1")).thenReturn(Optional.of(c));
        when(t.accounts.enabledProvider(7L)).thenReturn(PaymentProvider.SUMUP);
        when(t.accounts.requireAgency(7L)).thenReturn(new AgencyJpa("Pizzeria", "Pizzeria Da Mario"));
        when(t.repo.findByComandIdAndStatusInOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(t.repo.countByComandId("cmd_1")).thenReturn(2L);
        when(t.sumup.createCheckout(eq(creds), anyString(), eq(1500L), eq("EUR"), anyString(), anyString(), anyString()))
                .thenReturn(new SumUpCheckout("co_new", "ref", "PENDING", new BigDecimal("15.00"), "EUR", "MC123",
                        "https://pay.sumup.com/b2c/X", List.of()));

        PaymentIntentResponse resp = t.service.createIntent(7L, "pizzeria", null, "cmd_1", "eur");

        ArgumentCaptor<String> ref = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> returnUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> redirectUrl = ArgumentCaptor.forClass(String.class);
        verify(t.sumup).createCheckout(eq(creds), ref.capture(), eq(1500L), eq("EUR"), anyString(),
                returnUrl.capture(), redirectUrl.capture());
        assertTrue(ref.getValue().startsWith("cmd_1-2-"));
        assertEquals("https://api.example.com/api/payments/webhook/sumup/tok_7", returnUrl.getValue());
        assertEquals("https://app.example.com/pizzeria/payment/cmd_1?provider=sumup", redirectUrl.getValue());

        PaymentIntentResponse.SumUp s = assertInstanceOf(PaymentIntentResponse.SumUp.class, resp);
        assertEquals("SUMUP", s.provider());
        assertEquals("co_new", s.checkoutId());
        assertEquals("https://pay.sumup.com/b2c/X", s.hostedCheckoutUrl());
        assertFalse(s.manualCapture());
        assertTrue(s.approvalRequired());

        ArgumentCaptor<PaymentJpa> saved = ArgumentCaptor.forClass(PaymentJpa.class);
        verify(t.repo).save(saved.capture());
        assertEquals(PaymentProvider.SUMUP, saved.getValue().getProvider());
        assertEquals(Boolean.TRUE, saved.getValue().getApprovalRequired());
        assertEquals("MC123", saved.getValue().getSumupMerchantCode());
    }
}
