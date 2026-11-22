package com.modules.ordermodule.request;

import com.modules.common.model.Request;
import com.modules.common.model.enums.ComandWaiterType;

import java.util.ArrayList;
import java.util.List;

public class AddComandWaiter implements Request {
    private List<AddComandOrder> orders = new ArrayList<AddComandOrder>();
    private long idTable = -1;
    private String name = "";
    private String address = "";
    private String phone = "";
    private String time = "";
    private int seats = 1;
    private ComandWaiterType comandWaiterType;
    // Domicilio (HOME), tutti facoltativi: senza coordinate la tariffa è 0 (o deliveryFeeCents se indicata)
    private Double lat;
    private Double lng;
    private String intercom;
    private String floor;
    private String deliveryNotes;
    /** CASH (predefinito) · CARD · ONLINE */
    private String paymentChoice;
    /** Tariffa decisa dallo staff (centesimi); null = calcolata dalla zona se ci sono le coordinate. */
    private Long deliveryFeeCents;

    public AddComandWaiter() {
    }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public String getIntercom() { return intercom; }
    public void setIntercom(String intercom) { this.intercom = intercom; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }
    public String getPaymentChoice() { return paymentChoice; }
    public void setPaymentChoice(String paymentChoice) { this.paymentChoice = paymentChoice; }
    public Long getDeliveryFeeCents() { return deliveryFeeCents; }
    public void setDeliveryFeeCents(Long deliveryFeeCents) { this.deliveryFeeCents = deliveryFeeCents; }

    public AddComandWaiter(List<AddComandOrder> orders, long idTable) {
        this.orders = orders;
        this.idTable = idTable;
    }

    public ComandWaiterType getComandWaiterType() {
        return comandWaiterType;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getSeats() {
        return seats;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public void setComandWaiterType(ComandWaiterType comandWaiterType) {
        this.comandWaiterType = comandWaiterType;
    }

    public long getIdTable() {
        return idTable;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getName() {
        return name;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<AddComandOrder> getOrders() {
        return orders;
    }

    public void setIdTable(long idTable) {
        this.idTable = idTable;
    }

    public void setOrders(List<AddComandOrder> orders) {
        this.orders = orders;
    }

    @Override
    public String toString() {
        return "AddComandWaiter{" +
                "orders=" + orders +
                ", idTable=" + idTable +
                ", name='" + name + '\'' +
                ", address='" + address + '\'' +
                ", phone='" + phone + '\'' +
                ", time='" + time + '\'' +
                ", comandWaiterType=" + comandWaiterType +
                '}';
    }

    @Override
    public boolean validate() {
        boolean flag = false;
        if (comandWaiterType.equals(ComandWaiterType.HOME)) {
            this.idTable = -1;
            flag = !name.trim().isEmpty() && !address.trim().isEmpty() && !phone.trim().isEmpty() && !time.trim().isEmpty();
        } else if (comandWaiterType.equals(ComandWaiterType.TAKE_AWAY)) {
            this.idTable = -1;
            this.address = "";
            flag = !name.trim().isEmpty() && !phone.trim().isEmpty() && !time.trim().isEmpty();
        } else {
            this.name = "";
            this.address = "";
            this.phone = "";
            this.time = "";
            flag = idTable > 0;
        }
        return flag && !orders.isEmpty();
    }
}
