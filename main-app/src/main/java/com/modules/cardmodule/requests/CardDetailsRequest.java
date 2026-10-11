package com.modules.cardmodule.requests;

/** Dati del titolare della tessera (tutti facoltativi). */
public record CardDetailsRequest(String holderName, String holderContact, String note) {

    public String validate() {
        if (holderName != null && holderName.length() > 80) return "Nome troppo lungo";
        if (holderContact != null && holderContact.length() > 120) return "Contatto troppo lungo";
        if (note != null && note.length() > 500) return "Nota troppo lunga";
        return null;
    }
}
