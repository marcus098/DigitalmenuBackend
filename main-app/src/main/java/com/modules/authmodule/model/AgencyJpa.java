package com.modules.authmodule.model;

import com.modules.common.model.db.Agency;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "agencies")
public class AgencyJpa extends Agency {
    //todo modificare per fargli gestire solo i dati dell'abbonamento e non gli stili

    public AgencyJpa(){
        super();
    }

    public AgencyJpa(String originalName, String name){
        super(originalName, name);
    }

    public AgencyJpa(String originalName, String name, String waitersUrl, String adminsUrl) {
        super(originalName, name, waitersUrl, adminsUrl);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    @Column(name="deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    @Column(name="created_at")
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public int getPacket() {
        return super.getPacket();
    }

    @Override
    @Column(name="billing_end_at")
    public OffsetDateTime getBillingEndAt() {
        return super.getBillingEndAt();
    }

    @Override
    @Column(name="trial_end_at")
    public OffsetDateTime getTrialEndAt() {
        return super.getTrialEndAt();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    @Column(name="original_name")
    public String getOriginalName() {
        return super.getOriginalName();
    }

    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    public boolean isTrial() {
        return super.isTrial();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setAddress(String address) {
        super.setAddress(address);
    }

    @Override
    public void setBillingEndAt(OffsetDateTime billingEndAt) {
        super.setBillingEndAt(billingEndAt);
    }

    @Override
    public void setOriginalName(String originalName) {
        super.setOriginalName(originalName);
    }

    @Override
    public void setPacket(int packet) {
        super.setPacket(packet);
    }

    @Override
    public void setTrialEndAt(OffsetDateTime trialEndAt) {
        super.setTrialEndAt(trialEndAt);
    }

    @Override
    public void setTrial(boolean trial) {
        super.setTrial(trial);
    }

    @Override
    public void setPhone(String phone) {
        super.setPhone(phone);
    }

    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @Override
    @Column(name="admins_url")
    public String getAdminsUrl() {
        return super.getAdminsUrl();
    }

    @Override
    @Column(name="waiters_url")
    public String getWaitersUrl() {
        return super.getWaitersUrl();
    }

    @Override
    public void setAdminsUrl(String adminsUrl) {
        super.setAdminsUrl(adminsUrl);
    }

    @Override
    public void setWaitersUrl(String waitersUrl) {
        super.setWaitersUrl(waitersUrl);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
