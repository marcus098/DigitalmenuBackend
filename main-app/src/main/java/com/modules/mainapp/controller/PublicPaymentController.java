package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.payment.dto.CreatePaymentIntentRequest;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.service.PaymentService;
import com.modules.mainapp.payment.service.StripeConnectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RequestMapping("/api/public/payments")
@RestController
public class PublicPaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StripeConnectService connectService;

    /**
     * Crea il PaymentIntent per una comanda. request.amountCents è IGNORATO: l'importo viene
     * ricalcolato lato server e restituito in {@code amountCents}.
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
                                request.getIdTable(),
                                request.getComandId(),
                                request.getCurrency()
                        );
                        return ResponseEntity.ok(resp);
                    } catch (ResponseStatusException e) {
                        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                                e.getReason() != null ? e.getReason() : "Errore"));
                    } catch (IllegalStateException e) {
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
                    boolean enabled = paymentService.isOnlinePaymentEnabled(idAgency);
                    return ResponseEntity.ok(Map.of(
                            "enabled", enabled,
                            "prepaymentTakeaway", enabled && connectService.isPrepaymentRequired(idAgency, true),
                            "prepaymentTable", enabled && connectService.isPrepaymentRequired(idAgency, false)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
