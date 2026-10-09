package com.modules.mainapp.payment.dto;

/**
 * GET/PUT /api/payments/settings. paymentsEnabled è in sola lettura: il prepagamento si può attivare solo se Stripe
 * è attivo (charges_enabled) per il locale.
 */
public record PrepaymentSettings(boolean prepaymentTakeaway, boolean prepaymentTable, boolean paymentsEnabled) {
}
