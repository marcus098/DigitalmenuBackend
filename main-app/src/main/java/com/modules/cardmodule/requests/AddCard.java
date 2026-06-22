package com.modules.cardmodule.requests;

public class AddCard {
    private boolean typePoints;
    private int scope;
    private Double priceForPoint;

    public AddCard(){

    }

    public boolean isTypePoints() {
        return typePoints;
    }

    public void setTypePoints(boolean typePoints) {
        this.typePoints = typePoints;
    }

    public int getScope() {
        return scope;
    }

    public void setScope(int scope) {
        this.scope = scope;
    }

    public Double getPriceForPoint() {
        return priceForPoint;
    }

    public void setPriceForPoint(Double priceForPoint) {
        this.priceForPoint = priceForPoint;
    }

    @Override
    public String toString() {
        return "AddCard{" +
                "typePoints=" + typePoints +
                ", scope=" + scope +
                ", priceForPoint=" + priceForPoint +
                '}';
    }
}
