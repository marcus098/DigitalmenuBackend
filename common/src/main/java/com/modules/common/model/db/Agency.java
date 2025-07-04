package com.modules.common.model.db;

import java.time.OffsetDateTime;


public abstract class Agency {
    protected Long id;
    protected String originalName;
    protected String name;
    protected boolean deleted;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected String address;
    protected String phone;
    protected boolean trial;
    protected OffsetDateTime trialEndAt;
    protected OffsetDateTime billingEndAt;
    protected int packet;
    protected String waitersUrl;
    protected String adminsUrl;

    public Agency(){

    }

    public Agency(String originalName, String name){
        this.name = name;
        this.originalName = originalName;
        this.createdAt = OffsetDateTime.now();
        this.deleted = false;
        this.address = "";
        this.trial = true;
        this.trialEndAt = OffsetDateTime.now().plusDays(90);
        this.billingEndAt = OffsetDateTime.now().plusDays(90);
        this.packet = 1;
    }

    public Agency(String originalName, String name, String waitersUrl, String adminsUrl){
        this.name = name;
        this.originalName = originalName;
        this.createdAt = OffsetDateTime.now();
        this.deleted = false;
        this.address = "";
        this.trial = true;
        this.trialEndAt = OffsetDateTime.now().plusDays(90);
        this.billingEndAt = OffsetDateTime.now().plusDays(90);
        this.packet = 1;
        this.waitersUrl = waitersUrl;
        this.adminsUrl = adminsUrl;
    }

    public String getAdminsUrl() {
        return adminsUrl;
    }

    public String getWaitersUrl() {
        return waitersUrl;
    }

    public void setAdminsUrl(String adminsUrl) {
        this.adminsUrl = adminsUrl;
    }

    public void setWaitersUrl(String waitersUrl) {
        this.waitersUrl = waitersUrl;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPacket() {
        return packet;
    }

    public OffsetDateTime getBillingEndAt() {
        return billingEndAt;
    }

    public OffsetDateTime getTrialEndAt() {
        return trialEndAt;
    }

    public void setPacket(int packet) {
        this.packet = packet;
    }

    public void setBillingEndAt(OffsetDateTime billingEndAt) {
        this.billingEndAt = billingEndAt;
    }

    public void setTrialEndAt(OffsetDateTime trialEndAt) {
        this.trialEndAt = trialEndAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isTrial() {
        return trial;
    }

    public void setTrial(boolean trial) {
        this.trial = trial;
    }

    public String getAddress() {
        return address;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPhone() {
        return phone;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

}
