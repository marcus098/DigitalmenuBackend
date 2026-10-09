package com.modules.mainapp.payment.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.Event;
import com.stripe.model.LoginLink;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Unico punto di accesso alle API Stripe. Usa un'istanza {@link StripeClient} (niente {@code Stripe.apiKey} globale)
 * e passa le idempotency key tramite {@link RequestOptions}. Isolato in un componente per poterlo mockare nei test.
 */
@Component
public class StripeGateway {

    private final String secretKey;
    private volatile StripeClient client;

    public StripeGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    /** true se è configurata una secret key reale (non vuota né placeholder). */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank() && !secretKey.contains("INSERISCI")
                && (secretKey.startsWith("sk_") || secretKey.startsWith("rk_"));
    }

    private StripeClient client() {
        if (!isConfigured()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        StripeClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) client = new StripeClient(secretKey);
                c = client;
            }
        }
        return c;
    }

    private static RequestOptions idempotent(String key) {
        return RequestOptions.builder().setIdempotencyKey(key).build();
    }

    public PaymentIntent createPaymentIntent(PaymentIntentCreateParams params, String idempotencyKey) throws StripeException {
        return client().paymentIntents().create(params, idempotent(idempotencyKey));
    }

    public PaymentIntent cancelPaymentIntent(String paymentIntentId) throws StripeException {
        return client().paymentIntents().cancel(paymentIntentId);
    }

    public Account createAccount(AccountCreateParams params, String idempotencyKey) throws StripeException {
        return client().accounts().create(params, idempotent(idempotencyKey));
    }

    public Account retrieveAccount(String accountId) throws StripeException {
        return client().accounts().retrieve(accountId);
    }

    public AccountLink createAccountLink(AccountLinkCreateParams params) throws StripeException {
        return client().accountLinks().create(params);
    }

    public LoginLink createLoginLink(String accountId) throws StripeException {
        return client().accounts().loginLinks().create(accountId);
    }

    public Refund createRefund(RefundCreateParams params, String idempotencyKey) throws StripeException {
        return client().refunds().create(params, idempotent(idempotencyKey));
    }

    /** Verifica la firma Stripe-Signature (non richiede la secret key API). */
    public Event constructEvent(String payload, String sigHeader, String webhookSecret) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
