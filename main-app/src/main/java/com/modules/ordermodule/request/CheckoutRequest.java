package com.modules.ordermodule.request;

/**
 * Chiusura conto dalla cassa. Subtotale e totale arrivano dal client perché in cassa si possono
 * modificare quantità e aggiungere articoli non presenti sulla comanda.
 *
 * @param discountMode      "PCT" (sconto %) o "FINAL" (prezzo finale digitato)
 * @param cardDiscountCents parte dello sconto coperta dai punti tessera
 * @param pointsToUse       tessera a punti: punti da scalare
 * @param redeemStamps      tessera a timbri: riscatta il premio
 * @param earn              accredita punti/timbro sul totale pagato
 * @param paymentMethod     opzionale: CASH, CARD, OTHER (assente = UNSPECIFIED). Solo per lo storico: il pagamento
 *                          avviene sul terminale/cassa del locale, mai tramite la piattaforma
 */
public record CheckoutRequest(long subtotalCents, long totalCents, String discountMode, Double discountPct,
                              long cardDiscountCents, Long cardId, int pointsToUse, boolean redeemStamps,
                              boolean earn, String paymentMethod) {

    /** Compatibilità con i client che non inviano il metodo di pagamento. */
    public CheckoutRequest(long subtotalCents, long totalCents, String discountMode, Double discountPct,
                           long cardDiscountCents, Long cardId, int pointsToUse, boolean redeemStamps, boolean earn) {
        this(subtotalCents, totalCents, discountMode, discountPct, cardDiscountCents, cardId, pointsToUse, redeemStamps, earn, null);
    }

    public static final java.util.Set<String> PAYMENT_METHODS = java.util.Set.of("CASH", "CARD", "OTHER", "UNSPECIFIED");

    /** Metodo normalizzato: assente o vuoto = UNSPECIFIED. */
    public String normalizedPaymentMethod() {
        return paymentMethod == null || paymentMethod.isBlank() ? "UNSPECIFIED" : paymentMethod.trim().toUpperCase();
    }

    private static final long MAX_CENTS = 100_000_000L; // 1 milione di euro: oltre è sicuramente un errore

    /** @return messaggio d'errore, o null se valido */
    public String validate() {
        if (subtotalCents < 0 || subtotalCents > MAX_CENTS) return "Subtotale non valido";
        if (totalCents < 0 || totalCents > MAX_CENTS) return "Totale non valido";
        if (!"PCT".equals(discountMode) && !"FINAL".equals(discountMode)) return "Modalità sconto non valida";
        if (discountPct != null && (discountPct < 0 || discountPct > 100)) return "Percentuale non valida";
        if (cardDiscountCents < 0 || cardDiscountCents > MAX_CENTS) return "Sconto tessera non valido";
        if (pointsToUse < 0) return "Punti non validi";
        if (!PAYMENT_METHODS.contains(normalizedPaymentMethod())) return "Metodo di pagamento non valido";
        return null;
    }
}
