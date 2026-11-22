package com.modules.deliverymodule.dto;

import java.util.List;

/**
 * GET /api/public/delivery/{localname}: quello che serve al menu cliente. Stessi campi di {@code DeliverySettings}
 * (src/deliveryTypes.ts) più le opzioni di pagamento effettive e lo stato di sospensione.
 *
 * @param paymentOptions         metodi che il cliente può scegliere ORA (ONLINE solo se i pagamenti online sono attivi)
 * @param onlinePaymentAvailable provider di pagamento online attivo per il locale
 * @param paused                 asporto/consegna sospesi dal locale ("Sospendi asporto"): nessuna fascia disponibile
 * @param maxKm                  raggio massimo servito (maxKm dell'ultima zona)
 * @param requirePhoneOtpTakeaway codice SMS per l'asporto: sempre false se la verifica non è attiva sul server
 * @param phoneVerification      verifica SMS del telefono attiva sul server (false = ordini senza codice)
 */
public record PublicDeliveryInfo(
        boolean enabled,
        Double originLat,
        Double originLng,
        String originAddress,
        List<DeliveryZone> zones,
        Long freeDeliveryOverCents,
        String paymentMode,
        List<String> onDeliveryMethods,
        int slotMinutes,
        int deliveriesPerSlot,
        int leadMinutes,
        boolean requirePhoneOtpTakeaway,
        List<String> paymentOptions,
        boolean onlinePaymentAvailable,
        boolean paused,
        double maxKm,
        boolean phoneVerification
) {}
