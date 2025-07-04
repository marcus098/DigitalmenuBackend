package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.TableEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("tables")
public class TableEntityR2dbc extends TableEntity {

    public TableEntityR2dbc() {
        super();
    }

    public TableEntityR2dbc(Long id, String name, int seats, boolean busy, String code, Long idAgency, int x, int y, int w, int h, String location) {
        super(id, name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityR2dbc(String name, int seats, boolean busy, String code, Long idAgency, int x, int y, int w, int h, String location) {
        super(name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityR2dbc(Long id, String name, int seats, boolean busy, String code, Long idAgency, int x, int y, int w, int h, String location, String sessionId) {
        super(id, name, seats, busy, code, idAgency, x, y, w, h, location, sessionId);
    }

    public TableEntityR2dbc(String name, int seats, boolean busy, String code, Long idAgency, int x, int y, int w, int h, String location, String sessionId) {
        super(name, seats, busy, code, idAgency, x, y, w, h, location, sessionId);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public String getSessionId() {
        return super.getSessionId();
    }

    @Override
    public void setSessionId(String sessionId) {
        super.setSessionId(sessionId);
    }

    @Override
    public int getSeats() {
        return super.getSeats();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setSeats(int seats) {
        super.setSeats(seats);
    }

    @Override
    public void setBusy(boolean busy) {
        super.setBusy(busy);
    }

    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public double getY() {
        return super.getY();
    }

    @Override
    public double getX() {
        return super.getX();
    }

    @Override
    public double getW() {
        return super.getW();
    }

    @Override
    public double getH() {
        return super.getH();
    }

    @Override
    public String getLocation() {
        return super.getLocation();
    }

    @Override
    public void setY(double y) {
        super.setY(y);
    }

    @Override
    public void setX(double x) {
        super.setX(x);
    }

    @Override
    public void setW(double w) {
        super.setW(w);
    }

    @Override
    public void setH(double h) {
        super.setH(h);
    }

    @Override
    public void setLocation(String location) {
        super.setLocation(location);
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
    public String getCode() {
        return super.getCode();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public boolean isBusy() {
        return super.isBusy();
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
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
    public void setCode(String code) {
        super.setCode(code);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
