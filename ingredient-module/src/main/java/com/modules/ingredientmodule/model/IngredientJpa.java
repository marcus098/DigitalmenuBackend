package com.modules.ingredientmodule.model;

import com.modules.common.dto.IngredientDto;
import com.modules.common.model.db.Ingredient;
import com.modules.common.utilities.Utilities;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "ingredients")
public class IngredientJpa extends Ingredient {
    public IngredientJpa() {
        super();
    }

    public IngredientJpa(String name, boolean available, boolean addable, boolean frozen, String allergens, long idAgency, Double price) {
        super(name, available, addable, frozen, allergens, idAgency, price);
    }

    public IngredientJpa(long id, String name, boolean available, boolean addable, boolean frozen, String allergens, long idAgency, Double price) {
        super(id, name, available, addable, frozen, allergens, idAgency, price);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Override
    public Long getId() {
        return super.getId();
    }

    @Column(nullable = false, name = "id_agency")
    @Override
    public long getIdAgency() {
        return super.getIdAgency();
    }

    @Column(nullable = false)
    @Override
    public String getName() {
        return super.getName();
    }

    @Column(nullable = false)
    @Override
    public Double getPrice() {
        return super.getPrice();
    }

    @Column(nullable = false)
    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Column(nullable = false)
    @Override
    public boolean isAddable() {
        return super.isAddable();
    }

    @Column(nullable = false)
    @Override
    public boolean isFrozen() {
        return super.isFrozen();
    }

    @Column(nullable = false, name="created_at")
    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Column(nullable = false, name = "position_progressive")
    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Override
    public void setPrice(Double price) {
        super.setPrice(price);
    }

    @Override
    @Column(name="deleted_at")
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
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
    public void setIdAgency(long idAgency) {
        super.setIdAgency(idAgency);
    }
}
