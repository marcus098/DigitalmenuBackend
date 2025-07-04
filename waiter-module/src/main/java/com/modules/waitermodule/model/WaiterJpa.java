package com.modules.waitermodule.model;

import com.modules.common.model.db.Waiter;
import jakarta.persistence.*;

@Entity
@Table(name = "waiters")
public class WaiterJpa extends Waiter {
    public WaiterJpa() {
        super();
    }

    public WaiterJpa(long idAgency, long idUser) {
        super(idAgency, idUser);
    }

    public WaiterJpa(long idAgency, long idUser, String code) {
        super(idAgency, idUser, code);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name = "id_agency")
    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false, name = "id_user")
    @Override
    public long getIdUser() {
        return super.getIdUser();
    }

    @Column(nullable = false)
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
