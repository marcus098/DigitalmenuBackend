package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class Waiter {
    protected Long id;
    protected long idAgency;
    protected long idUser;
    protected boolean confirmed;
    protected boolean codeConfirmed;
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


    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isCodeConfirmed() {
        return codeConfirmed;
    }

    public void setCodeConfirmed(boolean codeConfirmed) {
        this.codeConfirmed = codeConfirmed;
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
