package com.modules.tablemodule.model;

import com.modules.common.dto.TableDto;
import com.modules.common.model.db.TableEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tables")
public class TableEntityJpa extends TableEntity {
    public TableEntityJpa() {
        super();
    }

    public TableEntityJpa(TableDto tableDto, long idAgency) {
        this.id = tableDto.getId();
        this.name = tableDto.getName();
        this.seats = tableDto.getSeats();
        this.busy = tableDto.isBusy();
        this.code = tableDto.getCode();
        this.idAgency = idAgency;
        this.x = tableDto.getX();
        this.y = tableDto.getY();
        this.w = tableDto.getW();
        this.h = tableDto.getH();
        this.location = tableDto.getLocation();
    }

    public TableEntityJpa(Long id, String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location) {
        super(id, name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityJpa(String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location) {
        super(name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityJpa(String name, long idAgency, double x, double y, double w, double h, String location) {
        super(name, idAgency, x, y, w, h, location);
    }

    public TableEntityJpa(Long id, String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location, String sessionId) {
        super(id, name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityJpa(String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location, String sessionId) {
        super(name, seats, busy, code, idAgency, x, y, w, h, location);
    }

    public TableEntityJpa(String name, long idAgency, double x, double y, double w, double h, String location, String sessionId) {
        super(name, idAgency, x, y, w, h, location);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name = "id_agency")
    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false)
    @Override
    public String getName() {
        return super.getName();
    }

    @Column(nullable = false)
    @Override
    public String getCode() {
        return super.getCode();
    }

    @Column(nullable = false)
    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Column(nullable = false, name = "created_at")
    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public int getSeats() {
        return super.getSeats();
    }

    @Override
    public double getH() {
        return super.getH();
    }

    @Override
    public double getW() {
        return super.getW();
    }

    @Override
    public double getX() {
        return super.getX();
    }

    @Override
    public double getY() {
        return super.getY();
    }

    @Override
    public void setH(double h) {
        super.setH(h);
    }

    @Override
    public void setW(double w) {
        super.setW(w);
    }

    @Override
    public void setX(double x) {
        super.setX(x);
    }

    @Override
    public void setY(double y) {
        super.setY(y);
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
    @Column(name = "deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Column(name = "session_id")
    @Override
    public String getSessionId() {
        return super.getSessionId();
    }

    @Override
    public void setSessionId(String sessionId) {
        super.setSessionId(sessionId);
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
    public String getLocation() {
        return super.getLocation();
    }

    @Override
    public void setLocation(String location) {
        super.setLocation(location);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
