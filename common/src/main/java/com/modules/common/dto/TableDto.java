package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.modules.common.model.db.TableEntity;

public class TableDto {
    private Long id;
    private String name;
    private int seats;
    private boolean busy;
    private String code;
    private double x;
    private double y;
    private double w;
    private double h;
    private String location;
    @JsonIgnore
    private String sessionId;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public TableDto(){

    }

    public TableDto(TableEntity tableEntity) {
        this.id = tableEntity.getId();
        this.name = tableEntity.getName();
        this.seats = tableEntity.getSeats();
        this.busy = tableEntity.isBusy();
        this.code = tableEntity.getCode();
        this.x = tableEntity.getX();
        this.y = tableEntity.getY();
        this.w = tableEntity.getW();
        this.h = tableEntity.getH();
        this.location = tableEntity.getLocation();
        this.sessionId = tableEntity.getSessionId();
    }

    public TableDto(TableEntity tableEntity, String sessionUpdating, String changeType) {
        this.id = tableEntity.getId();
        this.name = tableEntity.getName();
        this.seats = tableEntity.getSeats();
        this.busy = tableEntity.isBusy();
        this.code = tableEntity.getCode();
        this.x = tableEntity.getX();
        this.y = tableEntity.getY();
        this.w = tableEntity.getW();
        this.h = tableEntity.getH();
        this.location = tableEntity.getLocation();
        this.sessionId = tableEntity.getSessionId();
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCode() {
        return code;
    }

    public int getSeats() {
        return seats;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isBusy() {
        return busy;
    }

    public double getX() {
        return x;
    }

    public double getW() {
        return w;
    }

    public double getH() {
        return h;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setH(double h) {
        this.h = h;
    }

    public void setW(double w) {
        this.w = w;
    }

    @Override
    public String toString() {
        return "TableDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", seats=" + seats +
                ", busy=" + busy +
                ", code='" + code + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                ", location='" + location + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
