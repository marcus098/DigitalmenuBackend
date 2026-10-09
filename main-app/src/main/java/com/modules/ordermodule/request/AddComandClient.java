package com.modules.ordermodule.request;

import java.util.ArrayList;
import java.util.List;

public class AddComandClient {
    private long tableId;
    private String clientSessionId;
    private String localname;
    private List<AddComandOrder> orders = new ArrayList<>();

    public String getLocalname() { return localname; }
    public void setLocalname(String localname) { this.localname = localname; }

    public long getTableId() { return tableId; }
    public void setTableId(long tableId) { this.tableId = tableId; }
    public List<AddComandOrder> getOrders() { return orders; }
    public void setOrders(List<AddComandOrder> orders) { this.orders = orders; }
    public String getClientSessionId() { return clientSessionId; }
    public void setClientSessionId(String clientSessionId) { this.clientSessionId = clientSessionId; }
}
