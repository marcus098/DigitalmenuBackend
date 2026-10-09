package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.mainapp.request.PublicTakeawayRequest;
import com.modules.ordermodule.exception.OrderRejectedException;
import com.modules.ordermodule.request.AddComandClient;
import com.modules.ordermodule.service.OrderComandService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RequestMapping("/api/public")
@RestController
public class PublicOrderController {

    @Autowired
    private OrderComandService orderComandService;

    @Autowired
    private IpRateLimiter rateLimiter;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/orders/insert")
    public ResponseEntity<?> insertClientOrder(
            @RequestBody AddComandClient request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        if (!rateLimiter.isAllowed(ip)) {
            return ResponseEntity.status(429).body(Map.of("message", "Troppe richieste. Riprova tra qualche minuto."));
        }

        if (request.getTableId() <= 0 || request.getOrders() == null || request.getOrders().isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "Payload non valido"));
        }

        try {
            OrderComandService.CreationResult created = orderComandService.addOrderFromClient(request);
            if (created == null) return ResponseEntity.status(500).body(Map.of("message", "Errore nella creazione dell'ordine"));
            return ResponseEntity.ok(body(created));
        } catch (OrderRejectedException e) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Risposta di creazione: {id, status, paymentRequired}. paymentRequired = true → il cliente deve pagare subito
     * (comanda in AWAIT_PAYMENT, annullata dopo 15 minuti se non pagata).
     */
    private static Map<String, Object> body(OrderComandService.CreationResult created) {
        return Map.of("id", created.id(),
                "status", created.status().name(),
                "paymentRequired", created.paymentRequired());
    }

    @PostMapping("/orders/takeaway/{localname}")
    public ResponseEntity<?> insertTakeawayOrder(
            @PathVariable String localname,
            @RequestBody PublicTakeawayRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        if (!rateLimiter.isAllowed(ip)) {
            return ResponseEntity.status(429).body(Map.of("message", "Troppe richieste. Riprova tra qualche minuto."));
        }

        if (request.getCustomerName() == null || request.getCustomerName().isBlank()
                || request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()
                || request.getOrders() == null || request.getOrders().isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("message", "Dati incompleti"));
        }

        return userRepository.findByUsernameAndDeleted(localname, false)
                .<ResponseEntity<?>>map(user -> {
                    OrderComandService.CreationResult created;
                    try {
                        created = orderComandService.addPublicTakeaway(
                                user.getIdAgency(),
                                request.getCustomerName(),
                                request.getCustomerPhone(),
                                request.getPickupTime(),
                                request.getOrders()
                        );
                    } catch (OrderRejectedException e) {
                        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
                    }
                    if (created == null) {
                        return ResponseEntity.status(500).body(Map.of("message", "Errore nella creazione dell'ordine asporto"));
                    }
                    return ResponseEntity.ok(body(created));
                })
                .orElse(ResponseEntity.status(404).body(Map.of("message", "Locale non trovato")));
    }

    /**
     * X-Forwarded-For è impostabile dal client: non lo leggiamo direttamente. Dietro proxy,
     * server.forward-headers-strategy fa sì che getRemoteAddr() restituisca già l'IP reale.
     */
    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
