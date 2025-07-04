package com.modules.webfluxmodule.models.db;

import com.modules.common.model.db.Ingredient;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.List;

@Table("ingredients")
public class IngredientR2dbc extends Ingredient {

    public IngredientR2dbc() {
        super();
    }

    public IngredientR2dbc(String name, boolean available, boolean addable, boolean frozen, String allergens, long idAgency, Double price) {
        super(name, available, addable, frozen, allergens, idAgency, price);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public Double getPrice() {
        return super.getPrice();
    }

    @Override
    public void setPrice(Double price) {
        super.setPrice(price);
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setPositionProgressive(int positionProgressive) {
        super.setPositionProgressive(positionProgressive);
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public String getAllergens() {
        return super.getAllergens();
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public void setAllergens(String allergens) {
        super.setAllergens(allergens);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setAvailable(boolean available) {
        super.setAvailable(available);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setFrozen(boolean frozen) {
        super.setFrozen(frozen);
    }

    @Override
    public void setAddable(boolean addable) {
        super.setAddable(addable);
    }

    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public boolean isFrozen() {
        return super.isFrozen();
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Override
    public boolean isAddable() {
        return super.isAddable();
    }
}
