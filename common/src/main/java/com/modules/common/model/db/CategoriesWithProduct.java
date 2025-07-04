package com.modules.common.model.db;

public class CategoriesWithProduct {
    private long category_id;
    private String category_name;
    private String category_description;
    private String category_image;
    private boolean category_available;
    private String products;
    private int category_progressive_number;

    public CategoriesWithProduct() {

    }

    public CategoriesWithProduct(long category_id, String category_name, String category_description, String category_image, boolean category_available, String products, int category_progressive_number) {
        this.category_id = category_id;
        this.category_name = category_name;
        this.category_description = category_description;
        this.category_image = category_image;
        this.category_progressive_number = category_progressive_number;
        this.category_available = category_available;
        this.products = products;
    }

    public int getCategory_progressive_number() {
        return category_progressive_number;
    }

    public void setCategory_progressive_number(int category_progressive_number) {
        this.category_progressive_number = category_progressive_number;
    }

    public long getCategory_id() {
        return category_id;
    }

    public void setCategory_id(long category_id) {
        this.category_id = category_id;
    }

    public String getCategory_name() {
        return category_name;
    }

    public void setCategory_name(String category_name) {
        this.category_name = category_name;
    }

    public String getCategory_description() {
        return category_description;
    }

    public void setCategory_description(String category_description) {
        this.category_description = category_description;
    }

    public String getCategory_image() {
        return category_image;
    }

    public void setCategory_image(String category_image) {
        this.category_image = category_image;
    }

    public boolean isCategory_available() {
        return category_available;
    }

    public void setCategory_available(boolean category_available) {
        this.category_available = category_available;
    }

    public String getProducts() {
        return products;
    }

    public void setProducts(String products) {
        this.products = products;
    }

    @Override
    public String toString() {
        return "CategoriesWithProduct{" +
                "category_id=" + category_id +
                ", category_name='" + category_name + '\'' +
                ", category_description='" + category_description + '\'' +
                ", category_image='" + category_image + '\'' +
                ", category_available=" + category_available +
                ", products='" + products + '\'' +
                '}';
    }
}
