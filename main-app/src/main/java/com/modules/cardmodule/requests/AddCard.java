package com.modules.cardmodule.requests;

public class AddCard {
    private boolean typePoints;
    private int scope;
    private Double priceForPoint;
    private String holderName;
    private String holderContact;
    private String note;

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

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getHolderContact() { return holderContact; }
    public void setHolderContact(String holderContact) { this.holderContact = holderContact; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    @Override
    public String toString() {
        return "AddCard{" +
                "typePoints=" + typePoints +
                ", scope=" + scope +
                ", priceForPoint=" + priceForPoint +
                '}';
    }
}
