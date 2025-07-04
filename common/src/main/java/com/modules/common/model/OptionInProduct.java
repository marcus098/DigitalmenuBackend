package com.modules.common.model;

public class OptionInProduct {
    private String name;
    private double price;
    private boolean isDefault;

    public OptionInProduct(String name, double price, boolean isDefault) {
        this.name = name;
        this.price = price;
        this.isDefault = isDefault;
    }

    public OptionInProduct() {
    }

    public OptionInProduct(String str, boolean check) {
        str = str.trim();
        if (str.startsWith("|"))
            str = str.substring(1).trim();

        if (str.endsWith("|"))
            str = str.substring(0, str.length() - 1).trim();

        String[] parts = str.split("\\|");

        if (parts.length == 3) {
            this.name = parts[0];
            this.price = Double.parseDouble(parts[1]);
            this.isDefault = parts[2].equals("1");
        } else {
            name = "-1";
            price = -1;
        }
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    //public boolean isDefault() {
    //    return isDefault;
    //}

    public boolean isIsDefault(){
        return isDefault;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public String convertInString() {
        String value = isDefault ? "1" : "0";
        return "|" + name + "|" + price + "|" + value + "|";
    }

    @Override
    public String toString() {
        return "OptionInProduct{" +
                "name='" + name + '\'' +
                ", price=" + price +
                ", isDefault=" + isDefault +
                '}';
    }
}
