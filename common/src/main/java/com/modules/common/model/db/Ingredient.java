package com.modules.common.model.db;

import java.time.OffsetDateTime;
import java.util.List;

public abstract class Ingredient {
    protected Long id;
    protected String name;
    protected Double price;
    protected boolean available;
    protected boolean addable;
    protected boolean frozen;
    protected String allergens;
    protected long idAgency;
    protected boolean deleted;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected int positionProgressive;

    public Ingredient() {}

    public Ingredient(String name, boolean available, boolean addable, boolean frozen, String allergens, long idAgency, Double price) {
        this.name = name;
        this.available = available;
        this.addable = addable;
        this.price = price;
        this.frozen = frozen;
        this.allergens = allergens;
        this.idAgency = idAgency;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.positionProgressive = 0;
    }

    public Ingredient(long id, String name, boolean available, boolean addable, boolean frozen, String allergens, long idAgency, Double price) {
        this.name = name;
        this.price = price;
        this.id = id;
        this.available = available;
        this.addable = addable;
        this.frozen = frozen;
        this.allergens = allergens;
        this.idAgency = idAgency;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
        this.positionProgressive = 0;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public int getPositionProgressive() {
        return positionProgressive;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setPositionProgressive(int positionProgressive) {
        this.positionProgressive = positionProgressive;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getAllergens() {
        return allergens;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setAllergens(String allergens) {
        this.allergens = allergens;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public void setAddable(boolean addable) {
        this.addable = addable;
    }

    public long getIdAgency() {
        return idAgency;
    }

    public void setIdAgency(long idAgency) {
        this.idAgency = idAgency;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isAddable() {
        return addable;
    }
}
