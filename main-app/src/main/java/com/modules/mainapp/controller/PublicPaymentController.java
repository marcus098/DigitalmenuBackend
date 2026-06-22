package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.mainapp.payment.dto.CreatePaymentIntentRequest;
import com.modules.mainapp.payment.dto.PaymentIntentResponse;
import com.modules.mainapp.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RequestMapping("/api/public/payments")
@RestController
public class PublicPaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/intent/{localname}")
    public ResponseEntity<?> createIntent(
            @PathVariable String localname,
            @RequestBody CreatePaymentIntentRequest request) {

        if (request.getAmountCents() <= 0) {
            return ResponseEntity.badRequest().body("Importo non valido");
        }

        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(user -> {
                    try {
                        PaymentIntentResponse resp = paymentService.createIntent(
                                user.getIdAgency(),
                                request.getIdTable(),
                                request.getComandId(),
                                request.getAmountCents(),
                                request.getCurrency()
                        );
                        return ResponseEntity.ok(resp);
                    } catch (IllegalStateException e) {
                        return ResponseEntity.status(503).body(e.getMessage());
                    } catch (Exception e) {
                        return ResponseEntity.status(500).body("Errore creazione pagamento: " + e.getMessage());
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
