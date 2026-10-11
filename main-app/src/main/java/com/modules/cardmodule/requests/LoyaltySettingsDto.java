package com.modules.cardmodule.requests;

import com.modules.cardmodule.models.LoyaltySettingsJpa;

import java.time.YearMonth;
import java.util.Set;

/** Lettura e salvataggio delle impostazioni tessere fedeltà del locale. */
public record LoyaltySettingsDto(Double eurosPerPoint, Double pointValue, Integer stampsForPrize, String stampsPrize,
                                 String programType, String expiryPolicy, Integer expiryMonth, Integer expiryDay,
                                 Integer inactivityMonths, Double minSpendForVisit, Boolean oneVisitPerDay) {

    public static final Set<String> PROGRAM_TYPES = Set.of("POINTS", "VISITS");
    public static final Set<String> EXPIRY_POLICIES = Set.of("NONE", "YEARLY", "INACTIVITY");

    public static LoyaltySettingsDto of(LoyaltySettingsJpa s) {
        if (s == null) return new LoyaltySettingsDto(null, null, null, null, null, null, null, null, null, null, null);
        return new LoyaltySettingsDto(s.getEurosPerPoint(), s.getPointValue(), s.getStampsForPrize(), s.getStampsPrize(),
                s.getProgramType(), s.getExpiryPolicy(), s.getExpiryMonth(), s.getExpiryDay(),
                s.getInactivityMonths(), s.getMinSpendForVisit(), s.getOneVisitPerDay());
    }

    /** @return messaggio d'errore, o null se valido */
    public String validate() {
        if (eurosPerPoint != null && (eurosPerPoint <= 0 || eurosPerPoint > 10_000)) return "€ per punto non valido";
        if (pointValue != null && (pointValue < 0 || pointValue > 10_000)) return "Valore del punto non valido";
        if (stampsForPrize != null && (stampsForPrize < 1 || stampsForPrize > 1000)) return "Numero di visite non valido";
        if (stampsPrize != null && stampsPrize.length() > 120) return "Descrizione premio troppo lunga";
        if (programType != null && !PROGRAM_TYPES.contains(programType)) return "Tipo di tessera non valido";
        if (expiryPolicy != null && !EXPIRY_POLICIES.contains(expiryPolicy)) return "Regola di scadenza non valida";
        if ("YEARLY".equals(expiryPolicy)) {
            if (expiryMonth == null || expiryMonth < 1 || expiryMonth > 12) return "Mese di azzeramento non valido";
            if (expiryDay == null || expiryDay < 1 || expiryDay > YearMonth.of(2024, expiryMonth).lengthOfMonth())
                return "Giorno di azzeramento non valido";
        }
        if ("INACTIVITY".equals(expiryPolicy) && (inactivityMonths == null || inactivityMonths < 1 || inactivityMonths > 60))
            return "Mesi di inattività non validi (1-60)";
        if (minSpendForVisit != null && (minSpendForVisit < 0 || minSpendForVisit > 10_000)) return "Spesa minima non valida";
        return null;
    }
}
