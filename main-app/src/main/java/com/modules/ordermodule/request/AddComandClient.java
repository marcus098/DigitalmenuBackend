package com.modules.ordermodule.request;

import java.util.ArrayList;
import java.util.List;

public class AddComandClient {
    private long tableId;
    private List<AddComandOrder> orders = new ArrayList<>();

    public long getTableId() { return tableId; }
    public void setTableId(long tableId) { this.tableId = tableId; }
    public List<AddComandOrder> getOrders() { return orders; }
    public void setOrders(List<AddComandOrder> orders) { this.orders = orders; }
}
