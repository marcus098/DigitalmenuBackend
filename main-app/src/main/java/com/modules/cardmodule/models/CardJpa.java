package com.modules.cardmodule.models;

import com.modules.common.model.db.Card;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "cards")
public class CardJpa extends Card {

    // Dettagli e statistiche (colonne nullable: le tessere già emesse restano valide con ddl-auto=update)
    private String holderName;
    private String holderContact;
    private String note;
    private Integer visitsCount;
    private Long totalSpentCents;
    private OffsetDateTime lastVisitAt;
    private OffsetDateTime lastActivityAt;
    /** Da quando si accumula il saldo attuale (creazione o ultimo azzeramento): serve per l'azzeramento annuale. */
    private OffsetDateTime balanceSince;

    public CardJpa(){
        super();
    }

    public CardJpa(String code, boolean typePoints, int actualValue, int scope, double priceForPoint, String qrCodeUrl, long idAgency, OffsetDateTime createdAt, OffsetDateTime deletedAt, boolean deleted) {
        super(code, typePoints, actualValue, scope, priceForPoint, qrCodeUrl, idAgency, createdAt, deletedAt, deleted);
    }

    public CardJpa(String code, boolean typePoints, int actualValue, int scope, double priceForPoint, String qrCodeUrl, long idAgency, OffsetDateTime createdAt) {
        super(code, typePoints, actualValue, scope, priceForPoint, qrCodeUrl, idAgency, createdAt);
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public long getId() {
        return super.getId();
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public double getPriceForPoint() {
        return super.getPriceForPoint();
    }

    @Override
    public int getActualValue() {
        return super.getActualValue();
    }

    @Override
    public int getScope() {
        return super.getScope();
    }

    @Override
    public String getCode() {
        return super.getCode();
    }

    @Override
    public String getQrCodeUrl() {
        return super.getQrCodeUrl();
    }

    @Override
    public boolean isTypePoints() {
        return super.isTypePoints();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setActualValue(int actualValue) {
        super.setActualValue(actualValue);
    }

    @Override
    public void setCode(String code) {
        super.setCode(code);
    }

    @Override
    public void setPriceForPoint(double priceForPoint) {
        super.setPriceForPoint(priceForPoint);
    }

    @Override
    public void setQrCodeUrl(String qrCodeUrl) {
        super.setQrCodeUrl(qrCodeUrl);
    }

    @Override
    public void setScope(int scope) {
        super.setScope(scope);
    }

    @Override
    public void setTypePoints(boolean typePoints) {
        super.setTypePoints(typePoints);
    }

    @Column(length = 80)
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    @Column(length = 120)
    public String getHolderContact() { return holderContact; }
    public void setHolderContact(String holderContact) { this.holderContact = holderContact; }

    @Column(length = 500)
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getVisitsCount() { return visitsCount; }
    public void setVisitsCount(Integer visitsCount) { this.visitsCount = visitsCount; }

    public Long getTotalSpentCents() { return totalSpentCents; }
    public void setTotalSpentCents(Long totalSpentCents) { this.totalSpentCents = totalSpentCents; }

    public OffsetDateTime getLastVisitAt() { return lastVisitAt; }
    public void setLastVisitAt(OffsetDateTime lastVisitAt) { this.lastVisitAt = lastVisitAt; }

    public OffsetDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(OffsetDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public OffsetDateTime getBalanceSince() { return balanceSince; }
    public void setBalanceSince(OffsetDateTime balanceSince) { this.balanceSince = balanceSince; }
}
