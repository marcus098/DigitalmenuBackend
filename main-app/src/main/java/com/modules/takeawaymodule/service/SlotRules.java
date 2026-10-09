package com.modules.takeawaymodule.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Regole pure di disponibilità di uno slot asporto (testabili senza infrastruttura).
 * La capacità è espressa in ORDINI; il limite prodotti è un vincolo extra opzionale (0 = disattivato).
 * Oltre la capacità normale c'è la "riserva": ordini accettati solo previa approvazione del locale.
 */
public final class SlotRules {

    public enum Availability { AVAILABLE, ON_REQUEST, FULL, CLOSED, PAST, PAUSED }

    /**
     * Un'autorizzazione Stripe (capture manuale) scade dopo ~7 giorni: con prepagamento la riserva è
     * consentita solo per slot entro questo numero di giorni da oggi.
     */
    public static final int MAX_RESERVE_DAYS_WITH_PREPAYMENT = 6;

    private SlotRules() {}

    /**
     * Esito per un ordine che porterebbe lo slot a {@code ordersAfter} ordini e {@code productsAfter} prodotti.
     * n ≤ max (e prodotti entro il limite) → AVAILABLE; n ≤ max + riserva → ON_REQUEST; oltre → FULL.
     */
    public static Availability capacity(int ordersAfter, int productsAfter, int maxOrders, int maxProducts,
                                        int reserve, boolean reserveAllowed) {
        boolean ordersOk = ordersAfter <= maxOrders;
        boolean productsOk = maxProducts <= 0 || productsAfter <= maxProducts;
        if (ordersOk && productsOk) return Availability.AVAILABLE;
        if (reserveAllowed && reserve > 0 && ordersAfter <= maxOrders + reserve) return Availability.ON_REQUEST;
        return Availability.FULL;
    }

    /** Stato completo di uno slot per un nuovo ordine di {@code newProducts} prodotti. */
    public static Availability evaluate(boolean paused, boolean closed, LocalDateTime slotStart, LocalDateTime now,
                                        int currentOrders, int currentProducts, int newProducts,
                                        int maxOrders, int maxProducts, int reserve, boolean reserveAllowed) {
        if (paused) return Availability.PAUSED;
        if (closed) return Availability.CLOSED;
        if (slotStart.isBefore(now)) return Availability.PAST;
        return capacity(currentOrders + 1, currentProducts + Math.max(1, newProducts),
                maxOrders, maxProducts, reserve, reserveAllowed);
    }

    /** La riserva è utilizzabile? Con prepagamento solo per slot entro {@link #MAX_RESERVE_DAYS_WITH_PREPAYMENT} giorni. */
    public static boolean reserveAllowed(int reserve, boolean prepayment, LocalDate slotDate, LocalDate today) {
        if (reserve <= 0) return false;
        return !prepayment || !slotDate.isAfter(today.plusDays(MAX_RESERVE_DAYS_WITH_PREPAYMENT));
    }
}
