package com.modules.common.model;

import java.util.ArrayList;
import java.util.List;

public class ProductToOrder {
    private long idProduct;
    private String productName;
    private long idCategory;
    private String categoryName;
    private OptionInProduct productOption;
    private int quantity;
    private List<IngredientOrder> ingredientsMinus = new ArrayList<>();
    private List<IngredientOrderPlus> ingredientsPlus = new ArrayList<>();
    private String note;

    public ProductToOrder() {}

    public ProductToOrder(long idProduct, String productName, long idCategory, String categoryName, OptionInProduct productOption, int quantity, String note) {
        this.idProduct = idProduct;
        this.note = note;
        this.productName = productName;
        this.idCategory = idCategory;
        this.categoryName = categoryName;
        this.productOption = productOption;
        this.quantity = quantity;
    }

    //public ProductToOrder(AddProductToOrder addProductToOrder, String productName, String categoryName, long idCategory, OptionInProduct option, List<IngredientOrder> ingredientOrder, List<IngredientOrderPlus> ingredientOrderPlus){
    //    this.idProduct = addProductToOrder.getIdProduct();
    //    this.productName = productName;
    //    this.productOption = option;
    //    this.idCategory = idCategory;
    //    this.categoryName = categoryName;
    //    this.ingredientsMinus = ingredientOrder;
    //    this.quantity = addProductToOrder.getQuantity();
    //    this.note = addProductToOrder.getNote();
    //    this.ingredientsPlus = ingredientOrderPlus;
    //}

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public OptionInProduct getProductOption() {
        return productOption;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public long getIdCategory() {
        return idCategory;
    }

    public int getQuantity() {
        return quantity;
    }

    public List<IngredientOrder> getIngredientsMinus() {
        return ingredientsMinus;
    }

    public List<IngredientOrderPlus> getIngredientsPlus() {
        return ingredientsPlus;
    }

    public String getProductName() {
        return productName;
    }

    public long getIdProduct() {
        return idProduct;
    }

    public void setProductOption(OptionInProduct productOption) {
        this.productOption = productOption;
    }

    public void setIdCategory(long idCategory) {
        this.idCategory = idCategory;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setIngredientsPlus(List<IngredientOrderPlus> ingredientsPlus) {
        this.ingredientsPlus = ingredientsPlus;
    }

    public void setIngredientsMinus(List<IngredientOrder> ingredientsMinus) {
        this.ingredientsMinus = ingredientsMinus;
    }

    public void setIdProduct(long idProduct) {
        this.idProduct = idProduct;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @Override
    public String toString() {
        return "ProductToOrder{" +
                "idProduct=" + idProduct +
                ", productName='" + productName + '\'' +
                ", idCategory=" + idCategory +
                ", categoryName='" + categoryName + '\'' +
                ", productOption=" + productOption +
                ", quantity=" + quantity +
                ", ingredientsMinus=" + ingredientsMinus +
                ", ingredientsPlus=" + ingredientsPlus +
                '}';
    }
}
