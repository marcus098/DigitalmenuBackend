package com.modules.ordermodule.request;

import java.util.ArrayList;
import java.util.List;

public class AddComandOrder {
    private List<AddProductToOrder> products = new ArrayList<>();

    public AddComandOrder() {}

    public AddComandOrder(List<AddProductToOrder> products) {
        this.products = products;
    }

    public List<AddProductToOrder> getProducts() {
        return products;
    }

    public void setProducts(List<AddProductToOrder> products) {
        this.products = products;
    }

    @Override
    public String toString() {
        return "AddComandOrder{" +
                "products=" + products +
                '}';
    }
}
