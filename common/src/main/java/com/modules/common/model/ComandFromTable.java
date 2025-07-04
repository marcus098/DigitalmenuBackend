package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.List;

public class ComandFromTable extends Comand{
    private long idTable;
    private String userKey;

    public ComandFromTable() {}

    public ComandFromTable(long idAgency, long idTable, String userKey) {
        super(idAgency, ComandType.FROM_TABLE);
        this.idTable = idTable;
        this.userKey = userKey;
    }

    public ComandFromTable(long idAgency, long idTable, String userKey, String tableSessionId) {
        super(idAgency, ComandType.FROM_TABLE, tableSessionId);
        this.idTable = idTable;
        this.userKey = userKey;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @Override
    public List<Order> getOrders() {
        return super.getOrders();
    }

    @Override
    public ComandStatus getStatus() {
        return super.getStatus();
    }

    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public String getId() {
        return super.getId();
    }

    public long getIdTable() {
        return idTable;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        super.setUpdatedAt(updatedAt);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setStatus(ComandStatus status) {
        super.setStatus(status);
    }

    @Override
    public void setOrders(List<Order> orders) {
        super.setOrders(orders);
    }

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    public void setIdTable(long idTable) {
        this.idTable = idTable;
    }

    @Override
    public String getTableSessionId() {
        return super.getTableSessionId();
    }

    @Override
    public ComandType getType() {
        return super.getType();
    }

    @Override
    public void setTableSessionId(String tableSessionId) {
        super.setTableSessionId(tableSessionId);
    }

    @Override
    public void setType(ComandType type) {
        super.setType(type);
    }

    @Override
    public String toString() {
        return "ComandFromTable{" +
                "idTable=" + idTable +
                '}';
    }
}
