package com.modules.cardmodule.requests;

import com.modules.cardmodule.models.LoyaltySettingsJpa;

/** Lettura e salvataggio delle impostazioni tessere fedeltà del locale. */
public record LoyaltySettingsDto(Double eurosPerPoint, Double pointValue, Integer stampsForPrize, String stampsPrize) {

    public static LoyaltySettingsDto of(LoyaltySettingsJpa s) {
        if (s == null) return new LoyaltySettingsDto(null, null, null, null);
        return new LoyaltySettingsDto(s.getEurosPerPoint(), s.getPointValue(), s.getStampsForPrize(), s.getStampsPrize());
    }

    /** @return messaggio d'errore, o null se valido */
    public String validate() {
        if (eurosPerPoint != null && (eurosPerPoint <= 0 || eurosPerPoint > 10_000)) return "€ per punto non valido";
        if (pointValue != null && (pointValue < 0 || pointValue > 10_000)) return "Valore del punto non valido";
        if (stampsForPrize != null && (stampsForPrize < 1 || stampsForPrize > 1000)) return "Numero di timbri non valido";
        if (stampsPrize != null && stampsPrize.length() > 120) return "Descrizione premio troppo lunga";
        return null;
    }
}
