package com.modules.deliverymodule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modules.takeawaymodule.dto.SlotConfigDto;

import java.util.List;
import java.util.Map;

/**
 * GET/PUT /api/delivery/settings (titolare). Stessi campi di {@code DeliverySettings} in src/deliveryTypes.ts
 * più {@code weeklyHours} (null = stessi orari dell'asporto), {@code onlinePaymentAvailable} e
 * {@code phoneVerification} (sola lettura).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliverySettingsDto {
    private boolean enabled;
    private Double originLat;
    private Double originLng;
    private String originAddress;
    private List<DeliveryZone> zones = List.of();
    private Long freeDeliveryOverCents;
    private String paymentMode = "ON_DELIVERY_ONLY";
    private List<String> onDeliveryMethods = List.of("CASH");
    private int slotMinutes = 15;
    private int deliveriesPerSlot = 4;
    private int leadMinutes = 30;
    private boolean requirePhoneOtpTakeaway;
    /** Calcolo del percorso per i rider (predefinito: disattivato). */
    private boolean routePlanning;
    /** null = stessi orari degli slot asporto. */
    private Map<String, List<SlotConfigDto.TimeRange>> weeklyHours;
    /** Sola lettura: provider di pagamento online attivo per il locale. */
    private Boolean onlinePaymentAvailable;
    /** Sola lettura: verifica SMS del telefono attiva sul server (false = l'opzione per l'asporto non ha effetto). */
    private Boolean phoneVerification;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Double getOriginLat() { return originLat; }
    public void setOriginLat(Double originLat) { this.originLat = originLat; }
    public Double getOriginLng() { return originLng; }
    public void setOriginLng(Double originLng) { this.originLng = originLng; }
    public String getOriginAddress() { return originAddress; }
    public void setOriginAddress(String originAddress) { this.originAddress = originAddress; }
    public List<DeliveryZone> getZones() { return zones; }
    public void setZones(List<DeliveryZone> zones) { this.zones = zones; }
    public Long getFreeDeliveryOverCents() { return freeDeliveryOverCents; }
    public void setFreeDeliveryOverCents(Long freeDeliveryOverCents) { this.freeDeliveryOverCents = freeDeliveryOverCents; }
    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }
    public List<String> getOnDeliveryMethods() { return onDeliveryMethods; }
    public void setOnDeliveryMethods(List<String> onDeliveryMethods) { this.onDeliveryMethods = onDeliveryMethods; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int slotMinutes) { this.slotMinutes = slotMinutes; }
    public int getDeliveriesPerSlot() { return deliveriesPerSlot; }
    public void setDeliveriesPerSlot(int deliveriesPerSlot) { this.deliveriesPerSlot = deliveriesPerSlot; }
    public int getLeadMinutes() { return leadMinutes; }
    public void setLeadMinutes(int leadMinutes) { this.leadMinutes = leadMinutes; }
    public boolean isRequirePhoneOtpTakeaway() { return requirePhoneOtpTakeaway; }
    public void setRequirePhoneOtpTakeaway(boolean requirePhoneOtpTakeaway) { this.requirePhoneOtpTakeaway = requirePhoneOtpTakeaway; }
    public boolean isRoutePlanning() { return routePlanning; }
    public void setRoutePlanning(boolean routePlanning) { this.routePlanning = routePlanning; }
    public Map<String, List<SlotConfigDto.TimeRange>> getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(Map<String, List<SlotConfigDto.TimeRange>> weeklyHours) { this.weeklyHours = weeklyHours; }
    public Boolean getOnlinePaymentAvailable() { return onlinePaymentAvailable; }
    public void setOnlinePaymentAvailable(Boolean onlinePaymentAvailable) { this.onlinePaymentAvailable = onlinePaymentAvailable; }
    public Boolean getPhoneVerification() { return phoneVerification; }
    public void setPhoneVerification(Boolean phoneVerification) { this.phoneVerification = phoneVerification; }
}
