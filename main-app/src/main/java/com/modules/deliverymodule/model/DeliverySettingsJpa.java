package com.modules.deliverymodule.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** Impostazioni della consegna a domicilio, una riga per locale (vedi docs/DOMICILIO.md). */
@Entity
@Table(name = "delivery_settings")
public class DeliverySettingsJpa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_agency", nullable = false, unique = true)
    private Long idAgency;

    @Column(name = "enabled", nullable = false, columnDefinition = "boolean default false")
    private boolean enabled = false;

    @Column(name = "origin_lat")
    private Double originLat;

    @Column(name = "origin_lng")
    private Double originLng;

    @Column(name = "origin_address", length = 300)
    private String originAddress;

    /** JSON [{maxKm, feeCents, minOrderCents}] ordinate per maxKm. */
    @Column(name = "zones", columnDefinition = "TEXT", nullable = false)
    private String zones = "[]";

    @Column(name = "free_delivery_over_cents")
    private Long freeDeliveryOverCents;

    /** ONLINE_REQUIRED · CUSTOMER_CHOICE · ON_DELIVERY_ONLY */
    @Column(name = "payment_mode", length = 30, nullable = false)
    private String paymentMode = "ON_DELIVERY_ONLY";

    /** JSON ["CASH","CARD"] */
    @Column(name = "on_delivery_methods", columnDefinition = "TEXT", nullable = false)
    private String onDeliveryMethods = "[\"CASH\"]";

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 15;

    @Column(name = "deliveries_per_slot", nullable = false)
    private int deliveriesPerSlot = 4;

    @Column(name = "lead_minutes", nullable = false)
    private int leadMinutes = 30;

    /** JSON come takeaway_slot_config.weekly_hours; null = stessi orari dell'asporto. */
    @Column(name = "weekly_hours", columnDefinition = "TEXT")
    private String weeklyHours;

    @Column(name = "require_phone_otp_takeaway", nullable = false)
    private boolean requirePhoneOtpTakeaway = false;

    /** Calcolo del percorso per i rider (ordine delle consegne in uscita + giro in Google Maps). */
    @Column(name = "route_planning", nullable = false, columnDefinition = "boolean default false")
    private boolean routePlanning = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public DeliverySettingsJpa() {}

    public DeliverySettingsJpa(Long idAgency) { this.idAgency = idAgency; }

    @PreUpdate
    public void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIdAgency() { return idAgency; }
    public void setIdAgency(Long idAgency) { this.idAgency = idAgency; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Double getOriginLat() { return originLat; }
    public void setOriginLat(Double originLat) { this.originLat = originLat; }
    public Double getOriginLng() { return originLng; }
    public void setOriginLng(Double originLng) { this.originLng = originLng; }
    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }
    public String getZones() { return zones; }
    public void setZones(String zones) { this.zones = zones; }
    public Long getFreeDeliveryOverCents() { return freeDeliveryOverCents; }
    public void setFreeDeliveryOverCents(Long v) { this.freeDeliveryOverCents = v; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public String getOnDeliveryMethods() { return onDeliveryMethods; }
    public void setOnDeliveryMethods(String onDeliveryMethods) { this.onDeliveryMethods = onDeliveryMethods; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public int getDeliveriesPerSlot() { return deliveriesPerSlot; }
    public void setDeliveriesPerSlot(int deliveriesPerSlot) { this.deliveriesPerSlot = deliveriesPerSlot; }
    public int getLeadMinutes() { return leadMinutes; }
    public void setLeadMinutes(int leadMinutes) { this.leadMinutes = leadMinutes; }
    public String getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(String weeklyHours) { this.weeklyHours = weeklyHours; }
    public boolean isRequirePhoneOtpTakeaway() { return requirePhoneOtpTakeaway; }
    public void setRequirePhoneOtpTakeaway(boolean v) { this.requirePhoneOtpTakeaway = v; }
    public boolean isRoutePlanning() { return routePlanning; }
    public void setRoutePlanning(boolean routePlanning) { this.routePlanning = routePlanning; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
