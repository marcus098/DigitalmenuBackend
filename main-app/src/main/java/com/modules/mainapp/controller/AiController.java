package com.modules.mainapp.controller;

import com.modules.aimodule.model.InsightKind;
import com.modules.aimodule.service.AiInsightService;
import com.modules.aimodule.service.LandingTextService;
import com.modules.aimodule.service.LandingTextService.LandingText;
import com.modules.aimodule.service.LandingTextService.LandingTextRequest;
import com.modules.aimodule.service.ProductDescriptionService;
import com.modules.aimodule.service.ProductDescriptionService.DescriptionRequest;
import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.translationmodule.ai.AiUnavailableException;
import com.modules.translationmodule.ai.OllamaClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AxiomAI (solo titolare): riepiloghi e consigli calcolati in Java e scritti dall'AI, descrizione dei piatti.
 * Le risposte hanno sempre uno status esplicito: il GlobalExceptionHandler trasformerebbe le ResponseStatusException in 500.
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/ai")
@RestController
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AiController {

    @Autowired
    private AiInsightService insightService;
    @Autowired
    private ProductDescriptionService descriptionService;
    @Autowired
    private LandingTextService landingTextService;
    @Autowired
    private OllamaClient ollama;
    @Autowired
    private AuthenticatedUserProvider auth;

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("aiEnabled", ollama.isEnabled()));
    }

    /** Riepiloghi dei periodi correnti (null se non ancora generati). */
    @GetMapping("/insights")
    public ResponseEntity<?> insights() {
        try {
            return ResponseEntity.ok(insightService.overview(auth.getAgencyId()));
        } catch (Exception e) {
            ErrorLog.logger.error("AxiomAI: lettura riepiloghi non riuscita", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile leggere i riepiloghi");
        }
    }

    /** kind = daily | weekly | menu-tips. 202 con il riepilogo PENDING. */
    @PostMapping("/insights/{kind}/refresh")
    public ResponseEntity<?> refresh(@PathVariable("kind") String kind) {
        InsightKind k = InsightKind.fromPath(kind).orElse(null);
        if (k == null) return error(HttpStatus.BAD_REQUEST, "Tipo di riepilogo non valido");
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(insightService.refresh(auth.getAgencyId(), k));
        } catch (AiInsightService.RateLimitedException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (Exception e) {
            ErrorLog.logger.error("AxiomAI: aggiornamento " + k + " non avviato", e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile avviare l'aggiornamento, riprova più tardi");
        }
    }

    /** Descrizione del piatto (sincrona, può richiedere fino a un minuto). */
    @PostMapping("/product-description")
    public ResponseEntity<?> productDescription(@RequestBody(required = false) DescriptionRequest req) {
        try {
            return ResponseEntity.ok(Map.of("description", descriptionService.describe(auth.getAgencyId(), req)));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ProductDescriptionService.TooManyRequestsException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (AiUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "L'assistente AI non è disponibile in questo momento");
        }
    }

    /** Testi del sito vetrina: kind = hero | about | announcement. Risponde {title, text}. */
    @PostMapping("/landing-text")
    public ResponseEntity<?> landingText(@RequestBody(required = false) LandingTextRequest req) {
        try {
            LandingText out = landingTextService.write(auth.getAgencyId(), req);
            return ResponseEntity.ok(Map.of("title", out.title(), "text", out.text()));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (LandingTextService.TooManyRequestsException e) {
            return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (AiUnavailableException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "L'assistente AI non è disponibile in questo momento");
        }
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
