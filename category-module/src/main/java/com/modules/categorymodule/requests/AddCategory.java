package com.modules.categorymodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class AddCategory implements Request {
    private String name;
    private String description;
    private List<Long> products = new ArrayList<>();
    private boolean available;
    private String image;

    public AddCategory() {}

    public List<Long> getProducts() {
        return products;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public void setProducts(List<Long> products) {
        this.products = products;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public String toString() {
        return "AddCategory{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", products=" + products +
                ", available=" + available +
                ", image='" + image + '\'' +
                '}';
    }

    @Override
    public boolean validate() {
        return name != null && !name.trim().isEmpty();
    }
}
