package com.modules.tablemodule.requests;

import com.modules.common.model.Request;

public class AddTable implements Request {
    private String name;
    private double x;
    private double y;
    private double w;
    private double h;
    private String location;

    public AddTable() {}

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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

    public void setX(double x) {
        this.x = x;
    }

    public void setH(double h) {
        this.h = h;
    }

    public void setW(double w) {
        this.w = w;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean validate() {
        return name != null && !name.isEmpty() && x != 0 && y != 0 && w > 0 && h > 0;
    }
}
