package com.modules.common.model.db;

import java.time.OffsetDateTime;

public abstract class TableEntity {
    protected Long id;
    protected String name;
    protected int seats;
    protected boolean busy;
    protected String code;
    protected boolean deleted;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected Long idAgency;
    protected double x;
    protected double y;
    protected double w;
    protected double h;
    protected String location;
    protected String sessionId;

    public TableEntity() {}

    public TableEntity(Long id, String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location) {
        this.id = id;
        this.name = name;
        this.seats = seats;
        this.busy = busy;
        this.code = code;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.idAgency = idAgency;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.location = location;
        this.sessionId = null;
    }

    public TableEntity(String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location) {
        this.name = name;
        this.location = location;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.seats = seats;
        this.busy = busy;
        this.code = code;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.idAgency = idAgency;
        this.sessionId = null;
    }

    public TableEntity(Long id, String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location, String sessionId) {
        this.id = id;
        this.name = name;
        this.seats = seats;
        this.busy = busy;
        this.code = code;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.idAgency = idAgency;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.location = location;
        this.sessionId = sessionId;
    }

    public TableEntity(String name, int seats, boolean busy, String code, Long idAgency, double x, double y, double w, double h, String location, String sessionId) {
        this.name = name;
        this.location = location;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.seats = seats;
        this.busy = busy;
        this.code = code;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.idAgency = idAgency;
        this.sessionId = sessionId;
    }

    public TableEntity(String name, long idAgency, double x, double y, double w, double h, String location){
        this.name = name;
        this.location = location;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.seats = 1;
        this.busy = false;
        this.code = "";
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.idAgency = idAgency;
        this.sessionId = "";
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public double getH() {
        return h;
    }

    public double getW() {
        return w;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setW(double w) {
        this.w = w;
    }

    public void setH(double h) {
        this.h = h;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public int getSeats() {
        return seats;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getCode() {
        return code;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
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

    public void setCode(String code) {
        this.code = code;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public String toString() {
        return "TableEntity{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", seats=" + seats +
                ", busy=" + busy +
                ", code='" + code + '\'' +
                ", deleted=" + deleted +
                ", createdAt=" + createdAt +
                ", deletedAt=" + deletedAt +
                ", idAgency=" + idAgency +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                ", location=" + location +
                '}';
    }
}
