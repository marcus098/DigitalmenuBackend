package com.modules.mainapp.controller;

import com.modules.cardmodule.service.CardService;
import com.modules.common.responses.DataResponse;
import com.modules.mainapp.config.IpRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Saldo della tessera per il cliente (pagina /cardStatus, aperta dal QR della tessera).
 * Pubblico ma in sola lettura e senza dati del titolare; limitato per IP contro la ricerca a tentativi dei codici.
 */
@RequestMapping("/api/public/cards")
@RestController
public class PublicCardController {

    private static final int MAX_REQUESTS = 30;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    @Autowired
    private CardService cardService;

    @Autowired
    private IpRateLimiter rateLimiter;

    @GetMapping("/{code}")
    public ResponseEntity<?> status(@PathVariable("code") String code, HttpServletRequest httpRequest) {
        if (!rateLimiter.isAllowed("card-status:" + httpRequest.getRemoteAddr(), MAX_REQUESTS, WINDOW_MS)) {
            return ResponseEntity.status(429).body(Map.of("message", "Troppe richieste, riprova tra qualche minuto"));
        }
        if (code == null || code.length() > 40) return ResponseEntity.notFound().build();
        return cardService.getPublicStatus(code.trim().toUpperCase())
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(new DataResponse<>(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
