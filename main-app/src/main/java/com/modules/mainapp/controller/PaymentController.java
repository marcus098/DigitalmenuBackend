package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.mainapp.payment.entity.PaymentJpa;
import com.modules.mainapp.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/payments")
@RestController
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

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

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            paymentService.handleWebhook(payload, sigHeader != null ? sigHeader : "");
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
