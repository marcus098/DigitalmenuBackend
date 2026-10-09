package com.modules.authmodule.model.superadmin;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Dati di abbonamento di un locale visibili e modificabili SOLO dal superadmin (mai esposti da API non superadmin).
 * Una riga per locale, creata alla prima modifica: se manca, lo stato è derivato da agencies.trial.
 */
@Entity
@Table(name = "agency_admin_info")
public class AgencyAdminInfoJpa {

    @Id
    @Column(name = "id_agency")
    private Long idAgency;

    @Column(name = "active_from")
    private LocalDate activeFrom;

    @Column(name = "subscription_number", length = 100)
    private String subscriptionNumber;

    @Column(name = "plan", length = 100)
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 16)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** id dell'utente superadmin che ha fatto l'ultima modifica. */
    @Column(name = "updated_by")
    private Long updatedBy;

    public AgencyAdminInfoJpa() {
    }

    public AgencyAdminInfoJpa(Long idAgency) {
        this.idAgency = idAgency;
    }

    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public LocalDate getActiveFrom() { return activeFrom; }
    public void setActiveFrom(LocalDate activeFrom) { this.activeFrom = activeFrom; }
    public String getSubscriptionNumber() { return subscriptionNumber; }
    public void setSubscriptionNumber(String subscriptionNumber) { this.subscriptionNumber = subscriptionNumber; }
    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }
    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
