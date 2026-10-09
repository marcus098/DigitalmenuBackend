package com.modules.mainapp.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * GET/PUT /api/payments/provider*: impostazioni del provider di pagamento del locale. Non contiene MAI segreti
 * (solo le ultime 4 cifre delle chiavi).
 *
 * @param enabled             pagamenti online effettivamente attivi (provider attivo configurato + cifratura disponibile)
 * @param encryptionAvailable il server ha la chiave di cifratura: senza, le credenziali non si possono salvare
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProviderSettings(String activeProvider, boolean enabled, boolean encryptionAvailable,
                               boolean prepaymentTakeaway, boolean prepaymentTable,
                               Stripe stripe, SumUp sumup) {

    /**
     * @param webhookStatus AUTO (creato da noi), MANUAL (secret incollato), MISSING (nessun secret), null se non configurato
     * @param webhookUrl    URL da impostare su Stripe in caso di configurazione manuale del webhook
     * @param verifiedAt    ISO-8601 (UTC) dell'ultima verifica riuscita della chiave
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Stripe(boolean configured, String publishableKey, String secretKeyLast4, Boolean livemode,
                         String accountId, String accountName, String webhookStatus, String webhookUrl,
                         String lastError, String verifiedAt) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SumUp(boolean configured, String merchantCode, String apiKeyLast4, String lastError,
                        String verifiedAt) {}
}
