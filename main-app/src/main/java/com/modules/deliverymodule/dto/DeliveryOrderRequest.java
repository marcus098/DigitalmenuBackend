package com.modules.deliverymodule.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.modules.ordermodule.request.AddComandOrder;

import java.util.List;

/** POST /api/public/orders/delivery/{localname} (vedi docs/DOMICILIO.md). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryOrderRequest {
    private String customerName;
    private String customerPhone;
    private String phoneToken;
    private Address address;
    private String deliveryTime;
    private String paymentChoice;
    private List<AddComandOrder> orders;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        private String label;
        private Double lat;
        private Double lng;
        private String intercom;
        private String floor;
        private String notes;

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
    }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getPhoneToken() { return phoneToken; }
    public void setPhoneToken(String phoneToken) { this.phoneToken = phoneToken; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
    public String getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(String deliveryTime) { this.deliveryTime = deliveryTime; }
    public String getPaymentChoice() { return paymentChoice; }
    public void setPaymentChoice(String paymentChoice) { this.paymentChoice = paymentChoice; }
    public List<AddComandOrder> getOrders() { return orders; }
    public void setOrders(List<AddComandOrder> orders) { this.orders = orders; }
}
