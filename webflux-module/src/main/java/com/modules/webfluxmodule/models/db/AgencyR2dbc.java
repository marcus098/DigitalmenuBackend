package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Agency;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("agencies")
public class AgencyR2dbc extends Agency {

    public AgencyR2dbc(){
        super();
    }

    public AgencyR2dbc(String originalName, String name){
        super(originalName, name);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public String getOriginalName() {
        return super.getOriginalName();
    }

    @Override
    public void setOriginalName(String originalName) {
        super.setOriginalName(originalName);
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public int getPacket() {
        return super.getPacket();
    }

    @Override
    public OffsetDateTime getBillingEndAt() {
        return super.getBillingEndAt();
    }

    @Override
    public OffsetDateTime getTrialEndAt() {
        return super.getTrialEndAt();
    }

    @Override
    public void setPacket(int packet) {
        super.setPacket(packet);
    }

    @Override
    public void setBillingEndAt(OffsetDateTime billingEndAt) {
        super.setBillingEndAt(billingEndAt);
    }

    @Override
    public void setTrialEndAt(OffsetDateTime trialEndAt) {
        super.setTrialEndAt(trialEndAt);
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public boolean isTrial() {
        return super.isTrial();
    }

    @Override
    public void setTrial(boolean trial) {
        super.setTrial(trial);
    }

    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @Override
    public void setAddress(String address) {
        super.setAddress(address);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
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
    public void setPhone(String phone) {
        super.setPhone(phone);
    }

}
