package com.modules.mainapp.payment.stripe;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Balance;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.WebhookEndpoint;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.WebhookEndpointCreateParams;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unico punto di accesso alle API Stripe. Ogni locale usa il PROPRIO account (chiave segreta del locale): un
 * {@link StripeClient} per locale, in cache per (idAgency, impronta della chiave) e invalidato al cambio credenziali.
 * Niente {@code Stripe.apiKey} globale. Isolato in un componente per poterlo mockare nei test.
 */
@Component
public class StripeGateway {

    /** Eventi sottoscritti dal webhook creato automaticamente sull'account del locale. */
    public static final List<WebhookEndpointCreateParams.EnabledEvent> WEBHOOK_EVENTS = List.of(
            WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__SUCCEEDED,
            WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__PAYMENT_FAILED,
            WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__AMOUNT_CAPTURABLE_UPDATED,
            WebhookEndpointCreateParams.EnabledEvent.PAYMENT_INTENT__CANCELED,
            WebhookEndpointCreateParams.EnabledEvent.CHARGE__REFUNDED);

    private record CachedClient(String fingerprint, StripeClient client) {}

    private final Map<Long, CachedClient> clients = new ConcurrentHashMap<>();

    /** Impronta (non reversibile) di una chiave: usata per cache e idempotency key, mai la chiave stessa. */
    public static String fingerprint(String secretKey) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(secretKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    StripeClient client(StripeCredentials creds) {
        if (creds == null || creds.secretKey() == null || creds.secretKey().isBlank()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        String fp = fingerprint(creds.secretKey());
        CachedClient cached = clients.get(creds.idAgency());
        if (cached != null && cached.fingerprint().equals(fp)) return cached.client();
        StripeClient c = new StripeClient(creds.secretKey());
        clients.put(creds.idAgency(), new CachedClient(fp, c));
        return c;
    }

    /** Da chiamare quando le credenziali del locale cambiano o vengono rimosse. */
    public void invalidate(long idAgency) {
        clients.remove(idAgency);
    }

    private static RequestOptions idempotent(String key) {
        return RequestOptions.builder().setIdempotencyKey(key).build();
    }

    // ── Verifica credenziali / webhook ─────────────────────────────────────

    /** Account proprietario della chiave (GET /v1/account). Richiede il permesso di lettura account. */
    public Account retrieveOwnAccount(StripeCredentials creds) throws StripeException {
        return client(creds).accounts().retrieveCurrent();
    }

    /** Fallback di verifica per chiavi limitate senza lettura account (GET /v1/balance). */
    public Balance retrieveBalance(StripeCredentials creds) throws StripeException {
        return client(creds).balance().retrieve();
    }

    public WebhookEndpoint createWebhookEndpoint(StripeCredentials creds, String url, String description) throws StripeException {
        WebhookEndpointCreateParams.Builder params = WebhookEndpointCreateParams.builder()
                .setUrl(url)
                .addAllEnabledEvent(WEBHOOK_EVENTS)
                .setDescription(description);
        // Eventi nella stessa versione API della libreria (deserializzazione senza sorprese)
        for (WebhookEndpointCreateParams.ApiVersion v : WebhookEndpointCreateParams.ApiVersion.values()) {
            if (Stripe.API_VERSION.equals(v.getValue())) {
                params.setApiVersion(v);
                break;
            }
        }
        return client(creds).webhookEndpoints().create(params.build());
    }

    public void deleteWebhookEndpoint(StripeCredentials creds, String endpointId) throws StripeException {
        client(creds).webhookEndpoints().delete(endpointId);
    }

    // ── Pagamenti ──────────────────────────────────────────────────────────

    public PaymentIntent createPaymentIntent(StripeCredentials creds, PaymentIntentCreateParams params,
                                             String idempotencyKey) throws StripeException {
        return client(creds).paymentIntents().create(params, idempotent(idempotencyKey));
    }

    public PaymentIntent cancelPaymentIntent(StripeCredentials creds, String paymentIntentId) throws StripeException {
        return client(creds).paymentIntents().cancel(paymentIntentId);
    }

    /** Incassa un intent con capture_method=manual (stato requires_capture). */
    public PaymentIntent capturePaymentIntent(StripeCredentials creds, String paymentIntentId,
                                              String idempotencyKey) throws StripeException {
        return client(creds).paymentIntents().capture(paymentIntentId,
                PaymentIntentCaptureParams.builder().build(), idempotent(idempotencyKey));
    }

    public PaymentIntent retrievePaymentIntent(StripeCredentials creds, String paymentIntentId) throws StripeException {
        return client(creds).paymentIntents().retrieve(paymentIntentId);
    }

    public Refund createRefund(StripeCredentials creds, RefundCreateParams params, String idempotencyKey) throws StripeException {
        return client(creds).refunds().create(params, idempotent(idempotencyKey));
    }

    /** Verifica la firma Stripe-Signature con il webhook secret del locale (non richiede la chiave API). */
    public Event constructEvent(String payload, String sigHeader, String webhookSecret) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
