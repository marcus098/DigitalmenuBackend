package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.mainapp.payment.dto.CreatePaymentIntentRequest;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.dto.PublicPaymentConfig;
import com.modules.mainapp.payment.entity.PaymentProvider;
import com.modules.mainapp.payment.service.PaymentAccountService;
import com.modules.mainapp.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RequestMapping("/api/public/payments")
@RestController
public class PublicPaymentController {

    /** Sync SumUp: max richieste per IP nella finestra (il frontend fa qualche polling al ritorno dal checkout). */
    private static final int SYNC_MAX_REQUESTS = 30;
    private static final long SYNC_WINDOW_MS = 60_000L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentAccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IpRateLimiter rateLimiter;

    /**
     * Crea il pagamento per una comanda presso il provider attivo del locale. request.amountCents è IGNORATO:
     * l'importo viene ricalcolato lato server e restituito in {@code amountCents}.
     * Risposta STRIPE: {provider, clientSecret, paymentIntentId, amountCents, manualCapture, publishableKey};
     * SUMUP: {provider, checkoutId, hostedCheckoutUrl, amountCents, manualCapture:false, approvalRequired}.
     */
    @PostMapping("/intent/{localname}")
    public ResponseEntity<?> createIntent(
            @PathVariable String localname,
            @RequestBody CreatePaymentIntentRequest request) {

        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(user -> {
                    try {
                        PaymentIntentResponse resp = paymentService.createIntent(
                                user.getIdAgency(),
                                localname,
                                request.getIdTable(),
                                request.getComandId(),
                                request.getCurrency()
                        );
                        return ResponseEntity.ok(resp);
                    } catch (ResponseStatusException e) {
                        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                                e.getReason() != null ? e.getReason() : "Errore"));
                    } catch (IllegalStateException e) {
                        ErrorLog.logger.warn("Pagamenti non disponibili per locale {}: {}", localname, e.getMessage());
                        return ResponseEntity.status(503).body(Map.of("message",
                                "Pagamenti online temporaneamente non disponibili"));
                    } catch (Exception e) {
                        ErrorLog.logger.error("Errore creazione pagamento per locale {}", localname, e);
                        return ResponseEntity.status(500).body(Map.of("message", "Errore creazione pagamento"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Il locale accetta pagamenti online? Il client mostra "Paga online" solo se enabled = true.
     * prepaymentTakeaway / prepaymentTable: pagamento anticipato obbligatorio per il canale (solo se enabled).
     */
    @GetMapping("/config/{localname}")
    public ResponseEntity<?> config(@PathVariable String localname) {
        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(user -> {
                    long idAgency = user.getIdAgency();
                    PaymentProvider provider = accountService.enabledProvider(idAgency);
                    boolean enabled = provider != PaymentProvider.NONE;
                    String pk = provider == PaymentProvider.STRIPE
                            ? accountService.findAccount(idAgency).map(a -> a.getStripePublishableKey()).orElse(null)
                            : null;
                    return ResponseEntity.ok(new PublicPaymentConfig(enabled, provider.name(), pk,
                            enabled && accountService.isPrepaymentRequired(idAgency, true),
                            enabled && accountService.isPrepaymentRequired(idAgency, false)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Al ritorno dal checkout SumUp: rilegge il checkout da SumUp (solo se appartiene al locale) e applica lo stato.
     * Risposta: {status: PENDING|PAID|FAILED|EXPIRED, comandId}.
     */
    @PostMapping("/sumup/{localname}/sync/{checkoutId}")
    public ResponseEntity<?> syncSumUp(@PathVariable String localname, @PathVariable String checkoutId,
                                       HttpServletRequest httpRequest) {
        // getRemoteAddr(): dietro proxy server.forward-headers-strategy restituisce già l'IP reale
        if (!rateLimiter.isAllowed("sumup-sync:" + httpRequest.getRemoteAddr(), SYNC_MAX_REQUESTS, SYNC_WINDOW_MS)) {
            return ResponseEntity.status(429).body(Map.of("message", "Troppe richieste: riprova tra poco"));
        }
        return userRepository.findByUsernameAndDeleted(localname, false)
                .<ResponseEntity<?>>map(user -> {
                    try {
                        return ResponseEntity.ok(paymentService.syncSumUpCheckout(user.getIdAgency(), checkoutId));
                    } catch (ResponseStatusException e) {
                        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                                e.getReason() != null ? e.getReason() : "Errore"));
                    } catch (Exception e) {
                        ErrorLog.logger.error("Errore sync checkout SumUp {} locale {}", checkoutId, localname, e);
                        return ResponseEntity.status(500).body(Map.of("message", "Errore verifica pagamento"));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
