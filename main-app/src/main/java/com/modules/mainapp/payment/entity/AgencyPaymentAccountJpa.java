package com.modules.mainapp.payment.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Credenziali di pagamento del locale (account Stripe o SumUp DEL LOCALE: i soldi non passano mai dalla piattaforma).
 * I segreti sono cifrati con {@link com.modules.mainapp.payment.crypto.SecretCipher} (colonne *_enc) e non vengono
 * mai serializzati: l'API espone solo {@link com.modules.mainapp.payment.dto.ProviderSettings}.
 */
@Entity
@Table(name = "agency_payment_accounts")
public class AgencyPaymentAccountJpa {

    @Id
    @Column(name = "id_agency")
    private Long idAgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_provider", nullable = false, length = 16)
    private PaymentProvider activeProvider = PaymentProvider.NONE;

    /** Token casuale (url-safe) usato negli URL dei webhook al posto dell'id del locale. */
    @Column(name = "webhook_token", nullable = false, unique = true, length = 64)
    private String webhookToken;

    // ── Stripe ──
    @Column(name = "stripe_publishable_key", length = 255)
    private String stripePublishableKey;
    @Column(name = "stripe_secret_key_enc", length = 1024)
    private String stripeSecretKeyEnc;
    @Column(name = "stripe_secret_key_last4", length = 8)
    private String stripeSecretKeyLast4;
    @Column(name = "stripe_webhook_secret_enc", length = 1024)
    private String stripeWebhookSecretEnc;
    /** Endpoint creato automaticamente sull'account del locale (null = secret inserito a mano o assente). */
    @Column(name = "stripe_webhook_endpoint_id")
    private String stripeWebhookEndpointId;
    @Column(name = "stripe_account_id")
    private String stripeAccountId;
    @Column(name = "stripe_account_name")
    private String stripeAccountName;
    @Column(name = "stripe_livemode")
    private Boolean stripeLivemode;
    @Column(name = "stripe_verified_at")
    private Instant stripeVerifiedAt;
    @Column(name = "stripe_last_error", length = 1000)
    private String stripeLastError;

    // ── SumUp ──
    @Column(name = "sumup_api_key_enc", length = 1024)
    private String sumupApiKeyEnc;
    @Column(name = "sumup_api_key_last4", length = 8)
    private String sumupApiKeyLast4;
    @Column(name = "sumup_merchant_code", length = 64)
    private String sumupMerchantCode;
    @Column(name = "sumup_verified_at")
    private Instant sumupVerifiedAt;
    @Column(name = "sumup_last_error", length = 1000)
    private String sumupLastError;

    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public PaymentProvider getActiveProvider() { return activeProvider != null ? activeProvider : PaymentProvider.NONE; }
    public void setActiveProvider(PaymentProvider activeProvider) { this.activeProvider = activeProvider; }
    public String getWebhookToken() { return webhookToken; }
    public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }
    public String getStripePublishableKey() { return stripePublishableKey; }
    public void setStripePublishableKey(String v) { this.stripePublishableKey = v; }
    public String getStripeSecretKeyEnc() { return stripeSecretKeyEnc; }
    public void setStripeSecretKeyEnc(String v) { this.stripeSecretKeyEnc = v; }
    public String getStripeSecretKeyLast4() { return stripeSecretKeyLast4; }
    public void setStripeSecretKeyLast4(String v) { this.stripeSecretKeyLast4 = v; }
    public String getStripeWebhookSecretEnc() { return stripeWebhookSecretEnc; }
    public void setStripeWebhookSecretEnc(String v) { this.stripeWebhookSecretEnc = v; }
    public String getStripeWebhookEndpointId() { return stripeWebhookEndpointId; }
    public void setStripeWebhookEndpointId(String v) { this.stripeWebhookEndpointId = v; }
    public String getStripeAccountId() { return stripeAccountId; }
    public void setStripeAccountId(String v) { this.stripeAccountId = v; }
    public String getStripeAccountName() { return stripeAccountName; }
    public void setStripeAccountName(String v) { this.stripeAccountName = v; }
    public Boolean getStripeLivemode() { return stripeLivemode; }
    public void setStripeLivemode(Boolean v) { this.stripeLivemode = v; }
    public Instant getStripeVerifiedAt() { return stripeVerifiedAt; }
    public void setStripeVerifiedAt(Instant v) { this.stripeVerifiedAt = v; }
    public String getStripeLastError() { return stripeLastError; }
    public void setStripeLastError(String v) { this.stripeLastError = v; }
    public String getSumupApiKeyEnc() { return sumupApiKeyEnc; }
    public void setSumupApiKeyEnc(String v) { this.sumupApiKeyEnc = v; }
    public String getSumupApiKeyLast4() { return sumupApiKeyLast4; }
    public void setSumupApiKeyLast4(String v) { this.sumupApiKeyLast4 = v; }
    public String getSumupMerchantCode() { return sumupMerchantCode; }
    public void setSumupMerchantCode(String v) { this.sumupMerchantCode = v; }
    public Instant getSumupVerifiedAt() { return sumupVerifiedAt; }
    public void setSumupVerifiedAt(Instant v) { this.sumupVerifiedAt = v; }
    public String getSumupLastError() { return sumupLastError; }
    public void setSumupLastError(String v) { this.sumupLastError = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Chiavi Stripe salvate e verificate (il webhook è valutato a parte). */
    public boolean hasStripeKeys() {
        return stripePublishableKey != null && stripeSecretKeyEnc != null && stripeVerifiedAt != null;
    }

    /** Stripe pronto per incassare: chiavi verificate + webhook secret (senza webhook i pagamenti non si confermano). */
    public boolean isStripeConfigured() {
        return hasStripeKeys() && stripeWebhookSecretEnc != null;
    }

    public boolean isSumupConfigured() {
        return sumupApiKeyEnc != null && sumupMerchantCode != null && !sumupMerchantCode.isBlank() && sumupVerifiedAt != null;
    }

    public boolean isConfigured(PaymentProvider p) {
        return switch (p) {
            case STRIPE -> isStripeConfigured();
            case SUMUP -> isSumupConfigured();
            case NONE -> false;
        };
    }
}
