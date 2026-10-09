package com.modules.mainapp.payment;

/**
 * Calcolo server-side del totale di una comanda, in centesimi.
 * Il pagamento NON usa mai l'importo inviato dal client.
 */
public interface ComandTotalCalculator {
    long computeTotalCents(String comandId);
}
