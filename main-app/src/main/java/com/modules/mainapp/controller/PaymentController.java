package com.modules.mainapp.controller;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.responses.DataResponse;
import com.modules.mainapp.payment.dto.RefundRequest;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.mainapp.payment.service.StripeConnectService;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.stripe.exception.SignatureVerificationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RequestMapping("/api/payments")
@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private StripeConnectService connectService;

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
        try {
            return ResponseEntity.ok(new DataResponse<>(
                    paymentService.refund(paymentId, body != null ? body.getAmountCents() : null)));
        } catch (ResponseStatusException e) {
            return error(e);
        }
    }

    // ── Stripe Connect (onboarding del locale) ─────────────────────────────

    @PostMapping("/connect/onboard")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> onboard() {
        try {
            String url = connectService.createOnboardingLink(authUserProvider.getAgencyId());
            return ResponseEntity.ok(new DataResponse<>(Map.of("url", url)));
        } catch (ResponseStatusException e) {
            return error(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("message", "Stripe non configurato sulla piattaforma"));
        }
    }

    @GetMapping("/connect/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> connectStatus() {
        try {
            return ResponseEntity.ok(new DataResponse<>(connectService.refreshStatus(authUserProvider.getAgencyId())));
        } catch (ResponseStatusException e) {
            return error(e);
        }
    }

    @PostMapping("/connect/dashboard-link")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> dashboardLink() {
        try {
            String url = connectService.createDashboardLink(authUserProvider.getAgencyId());
            return ResponseEntity.ok(new DataResponse<>(Map.of("url", url)));
        } catch (ResponseStatusException e) {
            return error(e);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("message", "Stripe non configurato sulla piattaforma"));
        }
    }

    // ── Webhook (pubblici: permitAll in SecurityConf, autenticati dalla firma Stripe-Signature) ──

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            paymentService.handleWebhook(payload, sigHeader != null ? sigHeader : "");
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            ErrorLog.logger.warn("Stripe webhook: firma non valida");
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception e) {
            // 5xx => Stripe ritenterà la consegna (il webhook è idempotente)
            ErrorLog.logger.error("Stripe webhook: errore elaborazione", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    /** Eventi degli account collegati (Connect): account.updated. Secret: stripe.connect-webhook-secret. */
    @PostMapping("/webhook/connect")
    public ResponseEntity<String> connectWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            connectService.handleConnectWebhook(payload, sigHeader != null ? sigHeader : "");
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            ErrorLog.logger.warn("Stripe Connect webhook: firma non valida o secret non configurato");
            return ResponseEntity.badRequest().body("invalid signature");
        } catch (Exception e) {
            ErrorLog.logger.error("Stripe Connect webhook: errore elaborazione", e);
            return ResponseEntity.internalServerError().body("error");
        }
    }

    private static ResponseEntity<?> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("message", e.getReason() != null ? e.getReason() : "Errore"));
    }
}
