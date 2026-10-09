package com.modules.mainapp.payment.dto;

/**
 * Stato Stripe Connect del locale (pagina "Pagamenti online" della dashboard).
 *
 * @param platformConfigured la piattaforma ha una secret key Stripe configurata
 * @param connected          il locale ha un account Express (anche se l'onboarding non è completo)
 * @param chargesEnabled     il locale può incassare (abilita "Paga online" lato cliente)
 * @param detailsSubmitted   onboarding completato
 * @param applicationFeeBps  commissione piattaforma effettiva in basis points (100 = 1%)
 */
public record ConnectStatusResponse(boolean platformConfigured, boolean connected, boolean chargesEnabled,
                                    boolean detailsSubmitted, int applicationFeeBps) {
}
