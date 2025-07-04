package com.modules.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import com.modules.common.model.LongInteger;
import com.modules.common.model.db.Category;

import java.util.List;

public class CategoryDto {
    private long id;
    private String name;
    private String description;
    private String image;
    private boolean available;
    @JsonIgnore
    private List<Long> productsLong;
    private int progressiveNumber;
    private List<LongInteger> products;
    @JsonView(Views.Updating.class)
    private String sessionUpdating;
    @JsonView(Views.Updating.class)
    private String changeType;

    public CategoryDto() {}

    public CategoryDto(Category category, List<Long> productsLong) {
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
        this.image = category.getImage();
        this.available = category.isAvailable();
        this.productsLong = productsLong;
        this.progressiveNumber = category.getPositionProgressive();
    }

    public CategoryDto(List<LongInteger> products, Category category) {
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
        this.image = category.getImage();
        this.available = category.isAvailable();
        this.products = products;
        this.progressiveNumber = category.getPositionProgressive();
    }

    public CategoryDto(Category category, List<Long> productsLong, String sessionUpdating, String changeType) {
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
        this.image = category.getImage();
        this.available = category.isAvailable();
        this.productsLong = productsLong;
        this.progressiveNumber = category.getPositionProgressive();
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
    }

    public CategoryDto(List<LongInteger> products, Category category, String sessionUpdating, String changeType) {
        this.id = category.getId();
        this.name = category.getName();
        this.description = category.getDescription();
        this.image = category.getImage();
        this.available = category.isAvailable();
        this.products = products;
        this.progressiveNumber = category.getPositionProgressive();
        this.sessionUpdating = sessionUpdating;
        this.changeType = changeType;
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

    public int getProgressiveNumber() {
        return progressiveNumber;
    }

    public void setProgressiveNumber(int progressiveNumber) {
        this.progressiveNumber = progressiveNumber;
    }

    public String getDescription() {
        return description;
    }

    public List<Long> getProductsLong() {
        return productsLong;
    }

    public List<LongInteger> getProducts() {
        return products;
    }

    public void setProducts(List<LongInteger> products) {
        this.products = products;
    }

    public void setProductsLong(List<Long> productsLong) {
        this.productsLong = productsLong;
    }

    public String getImage() {
        return image;
    }

    public String getName() {
        return name;
    }

    public long getId() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "CategoryDto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", image='" + image + '\'' +
                ", available=" + available +
                ", productsLong=" + productsLong +
                ", progressiveNumber=" + progressiveNumber +
                ", products=" + products +
                ", sessionUpdating='" + sessionUpdating + '\'' +
                ", changeType='" + changeType + '\'' +
                '}';
    }
}
