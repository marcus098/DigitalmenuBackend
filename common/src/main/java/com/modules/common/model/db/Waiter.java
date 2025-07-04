package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Waiter {
    protected Long id;
    protected long idAgency;
    protected long idUser;
    protected boolean confirmed;
    protected String code;
    protected boolean deleted;
    protected OffsetDateTime deletedAt;
    protected OffsetDateTime createdAt;

    public Waiter() {}

    public Waiter(long idAgency, long idUser) {
        this.idAgency = idAgency;
        this.idUser = idUser;
    }

    public Waiter(long idAgency, long idUser, String code) {
        this.idAgency = idAgency;
        this.idUser = idUser;
        this.code = code;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getCode() {
        return code;
    }

    public Long getId() {
        return id;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public long getIdUser() {
        return idUser;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setIdUser(long idUser) {
        this.idUser = idUser;
    }

    @Override
    public String toString() {
        return "Waiter{" +
                "id=" + id +
                ", idAgency=" + idAgency +
                ", idUser=" + idUser +
                '}';
    }
}
