package com.modules.common.dto;

import com.modules.common.model.db.Card;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;

public class CardDto {
    private long id;
    private String code;
    private boolean typePoints;
    private int actualValue;
    private int scope;
    private double priceForPoint;
    private String qrCodeUrl;
    private String createdAt;
    private String holderName;
    private String holderContact;
    private String note;
    /** Conti chiusi con la tessera collegata. */
    private int visitsCount;
    private long totalSpentCents;
    private String lastVisitAt;
    /** Data in cui il saldo attuale verrà azzerato (regole di scadenza del locale), null = non scade. */
    private String expiresAt;

    public CardDto(long id, String code, boolean typePoints, int actualValue, int scope, double priceForPoint, String qrCodeUrl, String createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.code = code;
        this.typePoints = typePoints;
        this.actualValue = actualValue;
        this.scope = scope;
        this.priceForPoint = priceForPoint;
        this.qrCodeUrl = qrCodeUrl;
    }

    public CardDto(Card card) {
        this.id = card.getId();
        this.code = card.getCode();
        this.typePoints = card.isTypePoints();
        this.actualValue = card.getActualValue();
        this.scope = card.getScope();
        this.priceForPoint = card.getPriceForPoint();
        this.qrCodeUrl = card.getQrCodeUrl();
        String value = card.getCreatedAt().format(DateTimeFormatter.ISO_DATE);
        this.createdAt = value.substring(0, value.length() - 1);
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isTypePoints() {
        return typePoints;
    }

    public void setTypePoints(boolean typePoints) {
        this.typePoints = typePoints;
    }

    public int getActualValue() {
        return actualValue;
    }

    public void setActualValue(int actualValue) {
        this.actualValue = actualValue;
    }

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        this.scope = scope;
    }

    public double getPriceForPoint() {
        return priceForPoint;
    }

    public void setPriceForPoint(double priceForPoint) {
        this.priceForPoint = priceForPoint;
    }

    public String getQrCodeUrl() {
        return qrCodeUrl;
    }

    public void setQrCodeUrl(String qrCodeUrl) {
        this.qrCodeUrl = qrCodeUrl;
    }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderContact() { return holderContact; }
    public void setHolderContact(String holderContact) { this.holderContact = holderContact; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getVisitsCount() { return visitsCount; }
    public void setVisitsCount(int visitsCount) { this.visitsCount = visitsCount; }
    public long getTotalSpentCents() { return totalSpentCents; }
    public void setTotalSpentCents(long totalSpentCents) { this.totalSpentCents = totalSpentCents; }
    public String getLastVisitAt() { return lastVisitAt; }
    public void setLastVisitAt(String lastVisitAt) { this.lastVisitAt = lastVisitAt; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
