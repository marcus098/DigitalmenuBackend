package com.modules.mainapp.controller;

import com.modules.authmodule.repository.UserRepository;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.mainapp.request.PublicTakeawayRequest;
import com.modules.ordermodule.request.AddComandClient;
import com.modules.ordermodule.service.OrderComandService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<String> insertClientOrder(
            @RequestBody AddComandClient request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        if (!rateLimiter.isAllowed(ip)) {
            return ResponseEntity.status(429).body("Troppe richieste. Riprova tra qualche minuto.");
        }

        if (request.getTableId() <= 0 || request.getOrders() == null || request.getOrders().isEmpty()) {
            return ResponseEntity.status(400).body("Payload non valido");
        }

        String comandId = orderComandService.addOrderFromClient(request);
        return ResponseEntity.status(comandId == null ? 400 : 200).body(comandId);
    }

    @PostMapping("/orders/takeaway/{localname}")
    public ResponseEntity<String> insertTakeawayOrder(
            @PathVariable String localname,
            @RequestBody PublicTakeawayRequest request,
            HttpServletRequest httpRequest) {

        String ip = resolveClientIp(httpRequest);
        if (!rateLimiter.isAllowed(ip)) {
            return ResponseEntity.status(429).body("Troppe richieste. Riprova tra qualche minuto.");
        }

        if (request.getCustomerName() == null || request.getCustomerName().isBlank()
                || request.getCustomerPhone() == null || request.getCustomerPhone().isBlank()
                || request.getOrders() == null || request.getOrders().isEmpty()) {
            return ResponseEntity.status(400).body("Dati incompleti");
        }

        return userRepository.findByUsernameAndDeleted(localname, false)
                .map(user -> {
                    String comandId = orderComandService.addPublicTakeaway(
                            user.getIdAgency(),
                            request.getCustomerName(),
                            request.getCustomerPhone(),
                            request.getPickupTime(),
                            request.getOrders()
                    );
                    if (comandId == null) {
                        return ResponseEntity.status(500).body("Errore nella creazione dell'ordine asporto");
                    }
                    return ResponseEntity.ok(comandId);
                })
                .orElse(ResponseEntity.status(404).body("Locale non trovato"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
