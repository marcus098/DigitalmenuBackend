package com.modules.mainapp.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modules.authmodule.model.AgencyJpa;
import com.modules.authmodule.repository.AgencyRepository;
import com.modules.mainapp.payment.PaymentUrls;
import com.modules.mainapp.payment.crypto.SecretCipher;
import com.modules.mainapp.payment.dto.ProviderRequests;
import com.modules.mainapp.payment.dto.ProviderSettings;
import com.modules.mainapp.payment.entity.AgencyPaymentAccountJpa;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.repository.AgencyPaymentAccountRepository;
import com.modules.mainapp.payment.stripe.StripeCredentials;
import com.modules.mainapp.payment.stripe.StripeGateway;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.mainapp.payment.sumup.SumUpGateway;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Account;
import com.stripe.model.WebhookEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentAccountServiceTest {

    private static final String SK = "sk_test_51SecretKeyValue000000000000abcd";

    private AgencyPaymentAccountRepository repo;
    private AgencyRepository agencies;
    private StripeGateway stripe;
    private SumUpGateway sumup;
    private SecretCipher cipher;
    private PaymentAccountService service;
    private AgencyJpa agency;
    private AgencyPaymentAccountJpa stored;

    private static String key() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @BeforeEach
    void setUp() throws Exception {
        repo = mock(AgencyPaymentAccountRepository.class);
        agencies = mock(AgencyRepository.class);
        stripe = mock(StripeGateway.class);
        sumup = mock(SumUpGateway.class);
        cipher = new SecretCipher(key());
        service = new PaymentAccountService(repo, agencies, cipher, stripe, sumup,
                new PaymentUrls("https://api.example.com", "https://app.example.com"));
        agency = new AgencyJpa("Pizzeria", "pizzeria");
        when(agencies.findByIdAndDeleted(7L, false)).thenReturn(Optional.of(agency));
        when(repo.findById(7L)).thenAnswer(i -> Optional.ofNullable(stored));
        when(repo.save(any())).thenAnswer(i -> {
            stored = i.getArgument(0);
            return stored;
        });
        Account acc = new Account();
        acc.setId("acct_own");
        acc.setEmail("owner@example.com");
        when(stripe.retrieveOwnAccount(any())).thenReturn(acc);
    }

    private static WebhookEndpoint endpoint(String id) {
        WebhookEndpoint ep = new WebhookEndpoint();
        ep.setId(id);
        ep.setSecret("whsec_auto_" + id);
        return ep;
    }

    @Test
    void saveStripeCreatesWebhookAutomaticallyAndNeverExposesSecrets() throws Exception {
        when(stripe.createWebhookEndpoint(any(), anyString(), anyString())).thenReturn(endpoint("we_1"));

        ProviderSettings s = service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null));

        assertTrue(s.stripe().configured());
        assertEquals("AUTO", s.stripe().webhookStatus());
        assertEquals("abcd", s.stripe().secretKeyLast4());
        assertEquals("acct_own", s.stripe().accountId());
        assertEquals(Boolean.FALSE, s.stripe().livemode());
        assertTrue(s.stripe().webhookUrl().startsWith("https://api.example.com/api/payments/webhook/stripe/"));
        verify(stripe).createWebhookEndpoint(any(), eq(s.stripe().webhookUrl()), anyString());

        assertNotEquals(SK, stored.getStripeSecretKeyEnc());
        assertEquals(SK, cipher.decrypt(stored.getStripeSecretKeyEnc()));
        assertEquals("whsec_auto_we_1", cipher.decrypt(stored.getStripeWebhookSecretEnc()));
        String json = new ObjectMapper().writeValueAsString(s);
        assertFalse(json.contains(SK));
        assertFalse(json.contains("whsec_"));
        assertFalse(json.contains(stored.getStripeSecretKeyEnc()));
    }

    @Test
    void webhookCreationFailureFallsBackToManualSecret() throws Exception {
        when(stripe.createWebhookEndpoint(any(), anyString(), anyString()))
                .thenThrow(new InvalidRequestException("URL non raggiungibile", null, null, null, 400, null));

        ProviderSettings s = service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, "whsec_manual"));

        assertEquals("MANUAL", s.stripe().webhookStatus());
        assertTrue(s.stripe().configured());
        assertNull(s.stripe().lastError());
        assertEquals("whsec_manual", cipher.decrypt(stored.getStripeWebhookSecretEnc()));
    }

    @Test
    void webhookCreationFailureWithoutManualSecretIsMissing() throws Exception {
        when(stripe.createWebhookEndpoint(any(), anyString(), anyString()))
                .thenThrow(new InvalidRequestException("Invalid URL", null, null, null, 400, null));

        ProviderSettings s = service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null));

        assertEquals("MISSING", s.stripe().webhookStatus());
        assertFalse(s.stripe().configured());
        assertNotNull(s.stripe().lastError());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.setActive(7, "STRIPE"));
        assertEquals(400, e.getStatusCode().value());
    }

    @Test
    void resaveWithSameKeyKeepsAutoWebhook() throws Exception {
        when(stripe.createWebhookEndpoint(any(), anyString(), anyString())).thenReturn(endpoint("we_1"));
        service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null));

        ProviderSettings s = service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", null, null));

        assertEquals("AUTO", s.stripe().webhookStatus());
        verify(stripe, times(1)).createWebhookEndpoint(any(), anyString(), anyString());
    }

    @Test
    void newKeyReplacesPreviousAutoEndpoint() throws Exception {
        when(stripe.createWebhookEndpoint(any(), anyString(), anyString())).thenReturn(endpoint("we_1"), endpoint("we_2"));
        service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null));

        service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", "rk_test_restricted_key_value_9999", null));

        verify(stripe).deleteWebhookEndpoint(argThat((StripeCredentials c) -> SK.equals(c.secretKey())), eq("we_1"));
        assertEquals("we_2", stored.getStripeWebhookEndpointId());
        assertEquals("9999", stored.getStripeSecretKeyLast4());
    }

    @Test
    void invalidStripeKeysAre400() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.saveStripe(7,
                new ProviderRequests.StripeKeys("abc", SK, null))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.saveStripe(7,
                new ProviderRequests.StripeKeys("pk_test_abc", "sk_nope", null))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.saveStripe(7,
                new ProviderRequests.StripeKeys("pk_live_abc", SK, null))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.saveStripe(7,
                new ProviderRequests.StripeKeys("pk_test_abc", null, null))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.saveStripe(7,
                new ProviderRequests.StripeKeys("pk_test_abc", SK, "nope"))).getStatusCode().value());
    }

    @Test
    void keyRejectedByStripeIs400() throws Exception {
        when(stripe.retrieveOwnAccount(any())).thenThrow(new AuthenticationException("Invalid API Key", null, null, 401));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null)));
        assertEquals(400, e.getStatusCode().value());
        assertNull(stored == null ? null : stored.getStripeSecretKeyEnc());
    }

    @Test
    void savingWithoutEncryptionKeyIs503() {
        service = new PaymentAccountService(repo, agencies, new SecretCipher(""), stripe, sumup,
                new PaymentUrls("https://api.example.com", "https://app.example.com"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.saveStripe(7, new ProviderRequests.StripeKeys("pk_test_abc", SK, null)));
        assertEquals(503, e.getStatusCode().value());
        assertEquals(SecretCipher.NOT_CONFIGURED, e.getReason());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> service.saveSumUp(7, new ProviderRequests.SumUpKeys("sup_sk_0123456789abcdef", null))).getStatusCode().value());
    }

    @Test
    void sumupKeyIsValidatedAndMerchantCodeReadFromProfile() throws Exception {
        when(sumup.fetchMerchantCode("sup_sk_0123456789abcdef")).thenReturn("MC123");

        ProviderSettings s = service.saveSumUp(7, new ProviderRequests.SumUpKeys("sup_sk_0123456789abcdef", null));

        assertTrue(s.sumup().configured());
        assertEquals("MC123", s.sumup().merchantCode());
        assertEquals("cdef", s.sumup().apiKeyLast4());

        ProviderSettings active = service.setActive(7, "SUMUP");
        assertEquals("SUMUP", active.activeProvider());
        assertTrue(active.enabled());
        assertEquals(PaymentProvider.SUMUP, service.enabledProvider(7L));
    }

    @Test
    void sumupKeyRejectedIs400() throws Exception {
        when(sumup.fetchMerchantCode(anyString())).thenThrow(new SumUpException(401, "unauthorized"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.saveSumUp(7, new ProviderRequests.SumUpKeys("sup_sk_0123456789abcdef", null))).getStatusCode().value());
    }

    @Test
    void switchingToNoneOrDeletingActiveProviderDisablesPrepayment() throws Exception {
        when(sumup.fetchMerchantCode(anyString())).thenReturn("MC123");
        service.saveSumUp(7, new ProviderRequests.SumUpKeys("sup_sk_0123456789abcdef", null));
        service.setActive(7, "SUMUP");
        service.updatePrepaymentSettings(7, true, true);
        assertTrue(service.isPrepaymentRequired(7, true));

        ProviderSettings s = service.deleteSumUp(7);

        assertEquals("NONE", s.activeProvider());
        assertFalse(s.enabled());
        assertFalse(s.prepaymentTakeaway());
        assertFalse(s.prepaymentTable());
        assertFalse(service.isPrepaymentRequired(7, true));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.updatePrepaymentSettings(7, true, false)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.setActive(7, "SUMUP")).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.setActive(7, "PAYPAL")).getStatusCode().value());
    }
}
