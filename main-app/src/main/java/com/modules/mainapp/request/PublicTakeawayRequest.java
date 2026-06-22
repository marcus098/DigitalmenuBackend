package com.modules.mainapp.request;

import com.modules.ordermodule.request.AddComandOrder;
import java.util.List;

public class PublicTakeawayRequest {
    private String customerName;
    private String customerPhone;
    private String pickupTime;
    private List<AddComandOrder> orders;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String v) { this.customerName = v; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
    public String getPickupTime() { return pickupTime; }
    public void setPickupTime(String v) { this.pickupTime = v; }
    public List<AddComandOrder> getOrders() { return orders; }
    public void setOrders(List<AddComandOrder> v) { this.orders = v; }
}
