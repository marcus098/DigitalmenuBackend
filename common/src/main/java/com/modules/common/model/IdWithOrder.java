package com.modules.common.model;

public class IdWithOrder {
    private long id;
    private long order;

    public IdWithOrder() {

    }

    public IdWithOrder(long id, long order) {
        this.id = id;
        this.order = order;
    }

    public long getId() {
        return id;
    }

    public long getOrder() {
        return order;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setOrder(long order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "IdWithOrder{" +
                "id=" + id +
                ", order=" + order +
                '}';
    }
}
