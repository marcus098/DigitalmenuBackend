package com.modules.tablemodule.requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTableRow {
    private double x;
    private double y;
    private double w;
    private double h;
    private long id;

    public UpdateTableRow(double x, double y, double w, double h, long id) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.id = id;
    }

    public UpdateTableRow(){

    }

    @Override
    public String toString() {
        return "UpdateTableRow{" +
                ", x=" + x +
                ", y=" + y +
                ", w=" + w +
                ", h=" + h +
                ", id='" + id + '\'' +
                '}';
    }
}
