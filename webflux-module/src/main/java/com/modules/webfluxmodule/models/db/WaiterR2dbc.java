package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Waiter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("waiters")
public class WaiterR2dbc extends Waiter {
    public WaiterR2dbc() {
        super();
    }

    public WaiterR2dbc(long idAgency, long idUser) {
        super(idAgency, idUser);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public boolean isConfirmed() {
        return super.isConfirmed();
    }

    @Override
    public void setCode(String code) {
        super.setCode(code);
    }

    @Override
    public void setConfirmed(boolean confirmed) {
        super.setConfirmed(confirmed);
    }

    @Override
    public String getCode() {
        return super.getCode();
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public long getIdUser() {
        return super.getIdUser();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setIdUser(long idUser) {
        super.setIdUser(idUser);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
