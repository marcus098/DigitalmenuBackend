package com.modules.webfluxmodule.models.db;

import com.modules.common.model.OptionInProduct;
import com.modules.common.model.db.Product;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.List;

@Table("products")
public class ProductR2dbc extends Product {
    public ProductR2dbc() {
        super();
    }

    public ProductR2dbc(Long id, String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        super(id, name, description, options, image, idCategory, idAgency, tags, allergens, ingredients, available, positionProgressive);
    }

    public ProductR2dbc(String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        super(name, description, options, image, idCategory, idAgency, tags, allergens, ingredients, available, positionProgressive);
    }

    @Id
    @Override
    public Long getId() {
        return super.getId();
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted();
    }

    @Override
    public String getDescription() {
        return super.getDescription();
    }

    @Override
    public String getImage() {
        return super.getImage();
    }

    @Override
    public int getPositionProgressive() {
        return super.getPositionProgressive();
    }

    @Override
    public OffsetDateTime getDeletedAt() {
        return super.getDeletedAt();
    }

    @Override
    public OffsetDateTime getCreatedAt() {
        return super.getCreatedAt();
    }

    @Override
    public void setPositionProgressive(int positionProgressive) {
        super.setPositionProgressive(positionProgressive);
    }

    @Override
    public void setDeletedAt(OffsetDateTime deletedAt) {
        super.setDeletedAt(deletedAt);
    }

    @Override
    public void setDeleted(boolean deleted) {
        super.setDeleted(deleted);
    }

    @Override
    public void setCreatedAt(OffsetDateTime createdAt) {
        super.setCreatedAt(createdAt);
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public Long getIdAgency() {
        return super.getIdAgency();
    }

    @Override
    public String getOptions() {
        return super.getOptions();
    }

    @Override
    public Long getIdCategory() {
        return super.getIdCategory();
    }

    @Override
    public String getAllergens() {
        return super.getAllergens();
    }

    @Override
    public String getIngredients() {
        return super.getIngredients();
    }

    @Override
    public String getTags() {
        return super.getTags();
    }

    @Override
    public void setImage(String image) {
        super.setImage(image);
    }

    @Override
    public void setDescription(String description) {
        super.setDescription(description);
    }

    @Override
    public void setId(Long id) {
        super.setId(id);
    }

    @Override
    public void setAvailable(boolean available) {
        super.setAvailable(available);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public void setIdAgency(Long idAgency) {
        super.setIdAgency(idAgency);
    }

    @Override
    public void setIdCategory(Long idCategory) {
        super.setIdCategory(idCategory);
    }

    @Override
    public void setOptions(String options) {
        super.setOptions(options);
    }

    @Override
    public void setAllergens(String allergens) {
        super.setAllergens(allergens);
    }

    @Override
    public void setIngredients(String ingredients) {
        super.setIngredients(ingredients);
    }

    @Override
    public void setTags(String tags) {
        super.setTags(tags);
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
