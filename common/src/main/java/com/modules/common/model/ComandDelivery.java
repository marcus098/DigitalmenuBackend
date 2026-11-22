package com.modules.common.model;

import java.time.LocalDateTime;

/**
 * Snapshot della consegna a domicilio salvato nella comanda (vedi docs/DOMICILIO.md).
 * La comanda resta di tipo {@code comandWaiterType = HOME}; lo stato della consegna ({@link #stage}) è separato
 * dallo stato della comanda.
 */
public class ComandDelivery {

    public static final String STAGE_PREPARING = "PREPARING";
    public static final String STAGE_OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String STAGE_DELIVERED = "DELIVERED";

    public static final String PAY_ONLINE = "ONLINE";
    public static final String PAY_CASH = "CASH";
    public static final String PAY_CARD = "CARD";

    private String label;
    /** null per gli ordini a domicilio inseriti dal cameriere senza posizione. */
    private Double lat;
    private Double lng;
    private String intercom;
    private String floor;
    private String notes;
    /** Distanza in linea d'aria dal punto di partenza (km, 2 decimali); null se senza posizione. */
    private Double distanceKm;
    private long feeCents;
    /** ONLINE · CASH · CARD */
    private String paymentChoice;
    /** PREPARING · OUT_FOR_DELIVERY · DELIVERED */
    private String stage = STAGE_PREPARING;
    /** Pagamento alla consegna: il rider ha incassato (null = non ancora indicato). */
    private Boolean cashCollected;
    private LocalDateTime outForDeliveryAt;
    private LocalDateTime deliveredAt;
    /** Utente (rider / cameriere / titolare) che ha registrato l'ultimo cambio di stage. */
    private Long stageBy;

    public ComandDelivery() {}

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public String getIntercom() { return intercom; }
    public void setIntercom(String intercom) { this.intercom = intercom; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public long getFeeCents() { return feeCents; }
    public void setFeeCents(long feeCents) { this.feeCents = feeCents; }
    public String getPaymentChoice() { return paymentChoice; }
    public void setPaymentChoice(String paymentChoice) { this.paymentChoice = paymentChoice; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public Boolean getCashCollected() { return cashCollected; }
    public void setCashCollected(Boolean cashCollected) { this.cashCollected = cashCollected; }
    public LocalDateTime getOutForDeliveryAt() { return outForDeliveryAt; }
    public void setOutForDeliveryAt(LocalDateTime outForDeliveryAt) { this.outForDeliveryAt = outForDeliveryAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public Long getStageBy() { return stageBy; }
    public void setStageBy(Long stageBy) { this.stageBy = stageBy; }

    /** Pagamento alla consegna (contanti o POS del rider). */
    public boolean payOnDelivery() {
        return !PAY_ONLINE.equals(paymentChoice);
    }
}
