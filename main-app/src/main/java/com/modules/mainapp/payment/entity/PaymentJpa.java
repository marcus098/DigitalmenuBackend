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

    /** Account Connect di destinazione (locale) al momento della creazione dell'intent. */
    @Column(name = "stripe_account_id")
    private String stripeAccountId;

    /** Commissione piattaforma trattenuta (application_fee_amount), in centesimi. */
    @Column(name = "application_fee_cents")
    private Long applicationFeeCents;

    /** Totale rimborsato (da charge.refunded), in centesimi. */
    @Column(name = "refunded_cents")
    private Long refundedCents;

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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
