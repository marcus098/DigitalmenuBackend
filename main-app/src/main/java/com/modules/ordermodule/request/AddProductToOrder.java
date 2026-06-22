package com.modules.ordermodule.request;

import java.util.ArrayList;
import java.util.List;

public class AddProductToOrder {
    private long idProduct;
    private String productOption;
    private int quantity;
    private List<Long> ingredientsMinus = new ArrayList<>();
    private List<Long> ingredientsPlus = new ArrayList<>();
    private String note;

    public AddProductToOrder(){

    }

    public long getIdProduct() {
        return idProduct;
    }

    public void setIdProduct(long idProduct) {
        this.idProduct = idProduct;
    }

    public String getProductOption() {
        return productOption;
    }

    public void setProductOption(String productOption) {
        this.productOption = productOption;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public List<Long> getIngredientsMinus() {
        return ingredientsMinus;
    }

    public void setIngredientsMinus(List<Long> ingredientsMinus) {
        this.ingredientsMinus = ingredientsMinus;
    }

    public List<Long> getIngredientsPlus() {
        return ingredientsPlus;
    }

    public void setIngredientsPlus(List<Long> ingredientsPlus) {
        this.ingredientsPlus = ingredientsPlus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "AddProductToOrder{" +
                "idProduct=" + idProduct +
                ", productOption=" + productOption +
                ", quantity=" + quantity +
                ", ingredientsMinus=" + ingredientsMinus +
                ", ingredientsPlus=" + ingredientsPlus +
                ", note='" + note + '\'' +
                '}';
    }
}
