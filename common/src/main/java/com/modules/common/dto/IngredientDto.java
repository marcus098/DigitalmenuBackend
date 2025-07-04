package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonView;

import java.util.List;

public class IngredientDto {
    private Long id;
    private String name;
    private Double price;
    private boolean available;
    private boolean addable;
    private boolean frozen;
    private List<Integer> allergens;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public IngredientDto() {}

    public IngredientDto(Long id, String name, Double price, boolean available, boolean addable, boolean frozen, List<Integer> allergens) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.available = available;
        this.addable = addable;
        this.frozen = frozen;
        this.allergens = allergens;
    }

    public IngredientDto(Long id, String name, Double price, boolean available, boolean addable, boolean frozen, List<Integer> allergens, String sessionUpdating, String changeType) {
        this.id = id;
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
        this.name = name;
        this.price = price;
        this.available = available;
        this.addable = addable;
        this.frozen = frozen;
        this.allergens = allergens;
    }

    public String getSessionUpdating() {
        return sessionUpdating;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public void setSessionUpdating(String sessionUpdating) {
        this.sessionUpdating = sessionUpdating;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<Integer> getAllergens() {
        return allergens;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAddable(boolean addable) {
        this.addable = addable;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isAddable() {
        return addable;
    }

    @Override
    public String toString() {
        return "IngredientDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", available=" + available +
                ", addable=" + addable +
                ", frozen=" + frozen +
                ", allergens=" + allergens +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
