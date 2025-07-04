package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Card {
    private long id;
    private String code;
    private boolean typePoints;
    private int actualValue;
    private int scope;
    private double priceForPoint;
    private String qrCodeUrl;
    private long idAgency;
    private OffsetDateTime createdAt;
    private OffsetDateTime deletedAt;
    private boolean deleted;

    public Card(){

    }

    public Card(String code, boolean typePoints, int actualValue, int scope, double priceForPoint, String qrCodeUrl, long idAgency, OffsetDateTime createdAt, OffsetDateTime deletedAt, boolean deleted) {
        this.code = code;
        this.typePoints = typePoints;
        this.actualValue = actualValue;
        this.scope = scope;
        this.priceForPoint = priceForPoint;
        this.qrCodeUrl = qrCodeUrl;
        this.idAgency = idAgency;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.deleted = deleted;
    }

    public Card(String code, boolean typePoints, int actualValue, int scope, double priceForPoint, String qrCodeUrl, long idAgency, OffsetDateTime createdAt) {
        this.code = code;
        this.typePoints = typePoints;
        this.actualValue = actualValue;
        this.scope = scope;
        this.priceForPoint = priceForPoint;
        this.qrCodeUrl = qrCodeUrl;
        this.idAgency = idAgency;
        this.createdAt = createdAt;
        this.deleted = false;
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

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
