package com.modules.mainapp.payment;

/**
 * Commissione della piattaforma (application_fee_amount) per le destination charge Stripe Connect.
 * bps = basis points: 100 = 1%, 10000 = 100%.
 */
public final class ApplicationFeeCalculator {

    public static final int MAX_BPS = 10_000;

    private ApplicationFeeCalculator() {}

    /** bps effettivi: quelli del locale se impostati, altrimenti il default di piattaforma; sempre in [0, 10000]. */
    public static int effectiveBps(Integer agencyBps, int defaultBps) {
        int bps = agencyBps != null ? agencyBps : defaultBps;
        return Math.max(0, Math.min(MAX_BPS, bps));
    }

    /**
     * Commissione in centesimi, arrotondata per difetto (a favore del locale).
     * Non supera mai {@code amountCents - 1}: Stripe rifiuta una fee pari all'intero importo.
     */
    public static long feeCents(long amountCents, int bps) {
        if (amountCents <= 0 || bps <= 0) return 0;
        long fee = Math.multiplyExact(amountCents, (long) Math.min(bps, MAX_BPS)) / MAX_BPS;
        return Math.min(fee, amountCents - 1);
    }
}
