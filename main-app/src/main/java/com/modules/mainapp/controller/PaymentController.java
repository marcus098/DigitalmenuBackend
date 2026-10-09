package com.modules.mainapp.controller;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.DataResponse;
import com.modules.mainapp.payment.dto.ProviderRequests;
import com.modules.mainapp.payment.dto.RefundRequest;
import com.modules.mainapp.payment.service.PaymentAccountService;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.mainapp.payment.sumup.SumUpException;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.exception.SignatureVerificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

@RequestMapping("/api/payments")
@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentAccountService accountService;

    @Autowired
    private AuthenticatedUserProvider authUserProvider;

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(new DataResponse<>(paymentService.getPaymentsForAgency()));
    }

    @GetMapping("/today-total")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_WAITER')")
    public ResponseEntity<?> getTodayTotal() {
        long cents = paymentService.getTodayTotalCentsForAgency();
        return ResponseEntity.ok(new DataResponse<>(Map.of("amountCents", cents)));
    }

    /** Rimborso totale (body vuoto) o parziale ({amountCents}) di un pagamento del proprio locale. */
    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> refund(@PathVariable long paymentId, @RequestBody(required = false) RefundRequest body) {
        return wrap(() -> paymentService.refund(paymentId, body != null ? body.getAmountCents() : null));
    }

    // ── Impostazioni prepagamento del locale ───────────────────────────────

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> getSettings() {
        return wrap(() -> accountService.getPrepaymentSettings(authUserProvider.getAgencyId()));
    }

    /** Body: {prepaymentTakeaway, prepaymentTable}. 400 se si attiva il prepagamento senza provider attivo. */
    @PutMapping("/settings")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> body) {
        boolean takeaway = body != null && Boolean.TRUE.equals(body.get("prepaymentTakeaway"));
        boolean table = body != null && Boolean.TRUE.equals(body.get("prepaymentTable"));
        return wrap(() -> accountService.updatePrepaymentSettings(authUserProvider.getAgencyId(), takeaway, table));
    }

    // ── Provider di pagamento del locale (account Stripe / SumUp del locale) ──

    @GetMapping("/provider")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> getProvider() {
        return wrap(() -> accountService.getSettings(authUserProvider.getAgencyId()));
    }

    /** Body: {publishableKey, secretKey?, webhookSecret?}. 400 formato/chiave rifiutata, 503 cifratura assente. */
    @PutMapping("/provider/stripe")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> saveStripe(@RequestBody(required = false) ProviderRequests.StripeKeys body) {
        return wrap(() -> accountService.saveStripe(authUserProvider.getAgencyId(),
                body != null ? body : new ProviderRequests.StripeKeys(null, null, null)));
    }

    /** Body: {apiKey?, merchantCode?}. */
    @PutMapping("/provider/sumup")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> saveSumUp(@RequestBody(required = false) ProviderRequests.SumUpKeys body) {
        return wrap(() -> accountService.saveSumUp(authUserProvider.getAgencyId(),
                body != null ? body : new ProviderRequests.SumUpKeys(null, null)));
    }

    /** Body: {provider: NONE|STRIPE|SUMUP}. 400 se il provider non è configurato. */
    @PutMapping("/provider/active")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> setActive(@RequestBody(required = false) ProviderRequests.Active body) {
        return wrap(() -> accountService.setActive(authUserProvider.getAgencyId(), body != null ? body.provider() : null));
    }

    @DeleteMapping("/provider/stripe")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> deleteStripe() {
        return wrap(() -> accountService.deleteStripe(authUserProvider.getAgencyId()));
    }

    @DeleteMapping("/provider/sumup")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> deleteSumUp() {
        return wrap(() -> accountService.deleteSumUp(authUserProvider.getAgencyId()));
    }

    // ── Webhook (pubblici: permitAll in SecurityConf; il token nel path identifica il locale) ──

    /** Stripe: firma Stripe-Signature verificata con il webhook secret del locale. 5xx => Stripe ritenta. */
    @PostMapping("/webhook/stripe/{webhookToken}")
    public ResponseEntity<String> stripeWebhook(
            @PathVariable String webhookToken,
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            paymentService.handleStripeWebhook(webhookToken, payload, sigHeader != null ? sigHeader : "");
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            ErrorLog.logger.warn("Stripe webhook: firma non valida o token sconosciuto");
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception e) {
            ErrorLog.logger.error("Stripe webhook: errore elaborazione", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    /** SumUp: non firmato; lo stato viene riletto da SumUp. 200 per token/checkout sconosciuti, 5xx => SumUp ritenta. */
    @PostMapping("/webhook/sumup/{webhookToken}")
    public ResponseEntity<String> sumupWebhook(@PathVariable String webhookToken,
                                               @RequestBody(required = false) String payload) {
        try {
            paymentService.handleSumUpWebhook(webhookToken, payload);
            return ResponseEntity.ok("ok");
        } catch (SumUpException e) {
            ErrorLog.logger.warn("SumUp webhook: errore transitorio, verrà ritentato: {}", e.getMessage());
            return ResponseEntity.status(503).body("retry");
        } catch (Exception e) {
            ErrorLog.logger.error("SumUp webhook: errore elaborazione", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    private static ResponseEntity<?> wrap(Supplier<Object> action) {
        try {
            return ResponseEntity.ok(new DataResponse<>(action.get()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason() != null ? e.getReason() : "Errore"));
        }
    }
}
