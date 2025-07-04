package com.modules.tablemodule.requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTableRow {
    private String code;
    private int seats;
    private double x;
    private double y;
    private double w;
    private double h;
    private String id;
    private String location;
    private String name;
    private String status;

    public UpdateTableRow(String code, double x, double y, double w, double h, String id, String location, String name, int seats) {
        this.code = code;
        this.seats = seats;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.id = id;
        this.location = location;
        this.name = name;
    }

    public UpdateTableRow(){

    }

    @Override
    public String toString() {
        return "UpdateTableRow{" +
                "code='" + code + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                ", id='" + id + '\'' +
                ", location='" + location + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
