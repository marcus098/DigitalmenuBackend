package com.modules.ingredientmodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class UpdateIngredient implements Request {
    private long id;
    private String name;
    private boolean available;
    private boolean addable;
    private boolean frozen;
    private Double price;
    private List<Integer> allergens = new ArrayList<>();

    public List<Integer> getAllergens() {
        return allergens;
    }

    public Double getPrice() {
        return price;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setAllergens(List<Integer> allergens) {
        this.allergens = allergens;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddable(boolean addable) {
        this.addable = addable;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isAddable() {
        return addable;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public String toString() {
        return "UpdateIngredient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", available=" + available +
                ", addable=" + addable +
                ", frozen=" + frozen +
                ", price=" + price +
                ", allergens=" + allergens +
                '}';
    }

    @Override
    public boolean validate() {
        return id > 0 && name != null && !name.trim().isEmpty() && price >= 0;
    }
}
