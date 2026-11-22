package com.modules.deliverymodule.dto;

/** Zona di consegna: fino a {@code maxKm} in linea d'aria dal punto di partenza. */
public class DeliveryZone {
    private double maxKm;
    private long feeCents;
    private long minOrderCents;

    public DeliveryZone() {}

    public DeliveryZone(double maxKm, long feeCents, long minOrderCents) {
        this.maxKm = maxKm;
        this.feeCents = feeCents;
        this.minOrderCents = minOrderCents;
    }

    public double getMaxKm() { return maxKm; }
    public void setMaxKm(double maxKm) { this.maxKm = maxKm; }
    public long getFeeCents() { return feeCents; }
    public void setFeeCents(long feeCents) { this.feeCents = feeCents; }
    public long getMinOrderCents() { return minOrderCents; }
    public void setMinOrderCents(long minOrderCents) { this.minOrderCents = minOrderCents; }
}
