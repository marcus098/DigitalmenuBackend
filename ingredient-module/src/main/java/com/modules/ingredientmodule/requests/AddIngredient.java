package com.modules.ingredientmodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class AddIngredient implements Request {
    private String name;
    private boolean available;
    private boolean addable;
    private boolean frozen;
    private Double price;
    private List<Integer> allergens = new ArrayList<>();

    public AddIngredient() {}

    public String getName() {
        return name;
    }

    public Double getPrice() {
        return price;
    }

    public List<Integer> getAllergens() {
        return allergens;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isAddable() {
        return addable;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAddable(boolean addable) {
        this.addable = addable;
    }

    public void setAllergens(List<Integer> allergens) {
        this.allergens = allergens;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "AddIngredient{" +
                "name='" + name + '\'' +
                ", available=" + available +
                ", addable=" + addable +
                ", frozen=" + frozen +
                ", price=" + price +
                ", allergens=" + allergens +
                '}';
    }

    @Override
    public boolean validate() {
        return name != null && !name.trim().isEmpty() && price >= 0;
    }
}
