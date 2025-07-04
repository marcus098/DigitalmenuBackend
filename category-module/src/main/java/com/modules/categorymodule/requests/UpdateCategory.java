package com.modules.categorymodule.requests;

import com.modules.common.model.Request;

import java.util.ArrayList;
import java.util.List;

public class UpdateCategory implements Request {
    private long id;
    private String name;
    private String description;
    private List<String> products = new ArrayList<>();
    //private List<LongInteger> products = new ArrayList<>();
    private boolean available;
    private String image;

    public UpdateCategory() {}

    public String getDescription() {
        return description;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
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

    public String getName() {
        return name;
    }

    public List<String> getProducts() {
        return products;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setProducts(List<String> products) {
        this.products = products;
    }

    @Override
    public String toString() {
        return "UpdateCategory{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", products=" + products +
                ", available=" + available +
                ", image='" + image + '\'' +
                '}';
    }

    @Override
    public boolean validate() {
        return id > 0 && name!=null && !name.trim().isEmpty();
    }
}
