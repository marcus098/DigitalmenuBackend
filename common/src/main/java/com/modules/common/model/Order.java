package com.modules.common.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Order {
    private String id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String comandId;
    private String userId;
    private List<ProductToOrder> products = new ArrayList<>();

    public Order() {

    }

    public Order(String id, LocalDateTime createdAt, LocalDateTime updatedAt, String comandId, String userId, List<ProductToOrder> products) {
        this.id = id;
        this.products = products;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.comandId = comandId;
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getComandId() {
        return comandId;
    }

    public List<ProductToOrder> getProducts() {
        return products;
    }

    public String getUserId() {
        return userId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setProducts(List<ProductToOrder> products) {
        this.products = products;
    }

    public void setComandId(String comandId) {
        this.comandId = comandId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", comandId='" + comandId + '\'' +
                ", userId='" + userId + '\'' +
                ", products=" + products +
                '}';
    }
}
