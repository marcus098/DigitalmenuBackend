package com.modules.tablemodule.requests;

import com.modules.common.model.Request;

public class UpgradeTable implements Request {
    private Long id;
    private String name;
    private int seats;
    private boolean busy;
    private double x;
    private double y;
    private double w;
    private double h;
    private String location;

    public UpgradeTable() {}

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    public Long getId() {
        return id;
    }

    public int getSeats() {
        return seats;
    }

    public String getName() {
        return name;
    }

    public boolean isBusy() {
        return busy;
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

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "UpgradeTable{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", seats=" + seats +
                ", busy=" + busy +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                '}';
    }

    @Override
    public boolean validate() {
        if(location == null || location.trim().isEmpty())
            this.location = "Principale";
        return id > 0 && name != null && !name.trim().isEmpty() && w>0 && h>0;
    }
}
