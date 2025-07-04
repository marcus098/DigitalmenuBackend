package com.modules.common.model;

public class IngredientOrder {
    private long id;
    private String name;

    public IngredientOrder(long id, String name) {
        this.id = id;
        this.name = name;
    }

    public IngredientOrder() {

    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "IngredientOrder{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
