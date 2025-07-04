package com.modules.common.model;

import com.modules.common.model.enums.ComandStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public abstract class Comand {
    private String id;
    private ComandType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Order> orders;
    private ComandStatus status;
    private Long idAgency;
    private String tableSessionId;

    public Comand() {}

    public Comand(Long idAgency, ComandType comandType) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = new ArrayList<>();
        this.status = ComandStatus.AWAIT;
        this.type = comandType;
        this.tableSessionId = "";
    }

    public Comand(Long idAgency, ComandType comandType, String tableSessionId) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = new ArrayList<>();
        this.status = ComandStatus.AWAIT;
        this.type = comandType;
        this.tableSessionId = tableSessionId;
    }

    // per waiters
    public Comand(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = orders;
        this.status = status;
        this.type = comandType;
    }

    public Comand(Long idAgency, List<Order> orders, ComandStatus status, ComandType comandType, String tableSessionId) {
        this.idAgency = idAgency;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.orders = orders;
        this.status = status;
        this.type = comandType;
        this.tableSessionId = tableSessionId;
    }

    public ComandType getType() {
        return type;
    }

    public String getTableSessionId() {
        return tableSessionId;
    }

    public void setTableSessionId(String tableSessionId) {
        this.tableSessionId = tableSessionId;
    }

    public void setType(ComandType type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public String getId() {
        return id;
    }

    public ComandStatus getStatus() {
        return status;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
    }

    public void setStatus(ComandStatus status) {
        this.status = status;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "Comand{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", orders=" + orders +
                ", status=" + status +
                ", idAgency=" + idAgency +
                '}';
    }
}
