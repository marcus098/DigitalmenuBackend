package com.modules.mainapp.payment.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class PaymentJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "id_agency", nullable = false)
    private long idAgency;

    @Column(name = "id_table")
    private Long idTable;

    @Column(name = "comand_id")
    private String comandId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "currency", nullable = false)
    private String currency = "eur";

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_client_secret", length = 500)
    private String stripeClientSecret;

    /** Account Stripe del locale al momento della creazione dell'intent (per i pagamenti pre-2026-10: account Connect). */
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    /** @deprecated commissione piattaforma dell'era Stripe Connect: non più valorizzata (nessuna commissione). */
    @Deprecated
    @Column(name = "application_fee_cents")
    private Long applicationFeeCents;

    /** Totale rimborsato (da charge.refunded), in centesimi. */
    @Column(name = "refunded_cents")
    private Long refundedCents;

    /** Provider del pagamento. null = STRIPE (righe create prima dell'introduzione di SumUp). */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 16)
    private PaymentProvider provider;

    @Column(name = "sumup_checkout_id")
    private String sumupCheckoutId;

    @Column(name = "sumup_transaction_id")
    private String sumupTransactionId;

    @Column(name = "sumup_merchant_code", length = 64)
    private String sumupMerchantCode;

    @Column(name = "sumup_hosted_checkout_url", length = 1000)
    private String sumupHostedCheckoutUrl;

    /** Ordine nella riserva dello slot: va approvato dal locale prima dell'incasso definitivo. */
    @Column(name = "approval_required")
    private Boolean approvalRequired;

    /**
     * true = importo già addebitato al cliente mentre il pagamento è AUTHORIZED (SumUp non ha pre-autorizzazione):
     * all'approvazione diventa COMPLETED senza chiamate, al rifiuto viene rimborsato.
     */
    @Column(name = "captured_upfront")
    private Boolean capturedUpfront;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public long getId() { return id; }
    /** Solo per test / costruzione manuale. */
    public void setId(long id) { this.id = id; }
    public long getIdAgency() { return idAgency; }
    public void setIdAgency(long idAgency) { this.idAgency = idAgency; }
    public Long getIdTable() { return idTable; }
    public void setIdTable(Long idTable) { this.idTable = idTable; }
    public String getComandId() { return comandId; }
    public void setComandId(String comandId) { this.comandId = comandId; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public void setStripePaymentIntentId(String v) { this.stripePaymentIntentId = v; }
    /** Mai serializzato verso la dashboard: serve solo al cliente che paga. */
    @JsonIgnore
    public String getStripeClientSecret() { return stripeClientSecret; }
    public void setStripeClientSecret(String v) { this.stripeClientSecret = v; }
    public String getStripeAccountId() { return stripeAccountId; }
    public void setStripeAccountId(String stripeAccountId) { this.stripeAccountId = stripeAccountId; }
    public Long getApplicationFeeCents() { return applicationFeeCents; }
    public void setApplicationFeeCents(Long applicationFeeCents) { this.applicationFeeCents = applicationFeeCents; }
    public Long getRefundedCents() { return refundedCents; }
    public void setRefundedCents(Long refundedCents) { this.refundedCents = refundedCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public PaymentProvider getProvider() { return provider != null ? provider : PaymentProvider.STRIPE; }
    public void setProvider(PaymentProvider provider) { this.provider = provider; }
    public String getSumupCheckoutId() { return sumupCheckoutId; }
    public void setSumupCheckoutId(String v) { this.sumupCheckoutId = v; }
    public String getSumupTransactionId() { return sumupTransactionId; }
    public void setSumupTransactionId(String v) { this.sumupTransactionId = v; }
    @JsonIgnore
    public String getSumupMerchantCode() { return sumupMerchantCode; }
    public void setSumupMerchantCode(String v) { this.sumupMerchantCode = v; }
    @JsonIgnore
    public String getSumupHostedCheckoutUrl() { return sumupHostedCheckoutUrl; }
    public void setSumupHostedCheckoutUrl(String v) { this.sumupHostedCheckoutUrl = v; }
    public Boolean getApprovalRequired() { return approvalRequired; }
    public void setApprovalRequired(Boolean v) { this.approvalRequired = v; }
    public Boolean getCapturedUpfront() { return capturedUpfront; }
    public void setCapturedUpfront(Boolean v) { this.capturedUpfront = v; }

    /** Id del pagamento presso il provider (PaymentIntent Stripe o checkout SumUp). */
    public String externalRef() {
        return getProvider() == PaymentProvider.SUMUP ? sumupCheckoutId : stripePaymentIntentId;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
