package com.modules.common.model.db;

import com.modules.common.model.OptionInProduct;

import java.time.OffsetDateTime;
import java.util.List;

public abstract class Product {
    protected Long id;
    protected String name;
    protected String description;
    protected String options;
    protected String image;
    protected Long idCategory;
    protected Long idAgency;
    protected String tags;
    protected String allergens;
    protected String ingredients;
    protected boolean available;
    protected boolean deleted;
    protected OffsetDateTime createdAt;
    protected OffsetDateTime deletedAt;
    protected int positionProgressive;

    public Product() {
    }

    public Product(Long id, String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.idAgency = idAgency;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.positionProgressive = positionProgressive;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
    }

    public Product(String name, String description, String options, String image, Long idCategory, Long idAgency, String tags, String allergens, String ingredients, boolean available, int positionProgressive) {
        this.name = name;
        this.description = description;
        this.options = options;
        this.image = image;
        this.idCategory = idCategory;
        this.idAgency = idAgency;
        this.tags = tags;
        this.allergens = allergens;
        this.ingredients = ingredients;
        this.available = available;
        this.positionProgressive = positionProgressive;
        this.deleted = false;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    public int getPositionProgressive() {
        return positionProgressive;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setPositionProgressive(int positionProgressive) {
        this.positionProgressive = positionProgressive;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public Long getIdAgency() {
        return idAgency;
    }

    public String getOptions() {
        return options;
    }

    public Long getIdCategory() {
        return idCategory;
    }

    public String getAllergens() {
        return allergens;
    }

    public String getIngredients() {
        return ingredients;
    }

    public String getTags() {
        return tags;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIdAgency(Long idAgency) {
        this.idAgency = idAgency;
    }

    public void setIdCategory(Long idCategory) {
        this.idCategory = idCategory;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public void setAllergens(String allergens) {
        this.allergens = allergens;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", options=" + options +
                ", image='" + image + '\'' +
                ", idCategory=" + idCategory +
                ", idAgency=" + idAgency +
                ", tags='" + tags + '\'' +
                ", allergens='" + allergens + '\'' +
                ", ingredients='" + ingredients + '\'' +
                ", available=" + available +
                '}';
    }
}
