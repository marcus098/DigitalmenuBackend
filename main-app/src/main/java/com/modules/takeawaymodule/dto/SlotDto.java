package com.modules.takeawaymodule.dto;

/**
 * Slot calcolato per un giorno specifico.
 *  - time: "HH:mm"
 *  - status: AVAILABLE | FULL | CLOSED | PAST
 *  - orderCount: ordini totali (online + manuali)
 *  - productCount: prodotti totali (online + manuali)
 */
public class SlotDto {
    public enum Status { AVAILABLE, FULL, CLOSED, PAST }

    private String time;
    private Status status;
    private int orderCount;
    private int productCount;
    private int maxOrders;
    private int maxProducts;
    private int manualOrders;
    private int manualProducts;

    public SlotDto() {}

    public SlotDto(String time, Status status, int orderCount, int productCount, int maxOrders, int maxProducts) {
        this.time = time;
        this.status = status;
        this.orderCount = orderCount;
        this.productCount = productCount;
        this.maxOrders = maxOrders;
        this.maxProducts = maxProducts;
    }

    public String getTime() { return time; }
    public void setTime(String v) { this.time = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int v) { this.orderCount = v; }
    public int getProductCount() { return productCount; }
    public void setProductCount(int v) { this.productCount = v; }
    public int getMaxOrders() { return maxOrders; }
    public void setMaxOrders(int v) { this.maxOrders = v; }
    public int getMaxProducts() { return maxProducts; }
    public void setMaxProducts(int v) { this.maxProducts = v; }
    public int getManualOrders() { return manualOrders; }
    public void setManualOrders(int v) { this.manualOrders = v; }
    public int getManualProducts() { return manualProducts; }
    public void setManualProducts(int v) { this.manualProducts = v; }
}
