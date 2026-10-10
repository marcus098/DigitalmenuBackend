package com.modules.mainapp.controller;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.mainapp.contact.ContactRequest;
import com.modules.mainapp.contact.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Modulo "Contattaci" del sito vetrina (pagina "/"). Pubblico: coperto da /api/public/** in SecurityConf.
 */
@RequestMapping("/api/public/contact")
@RestController
public class PublicContactController {

    /** Max invii per IP nella finestra. */
    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_MS = 60 * 60 * 1000L; // 1 ora

    @Autowired
    private ContactService contactService;

    @Autowired
    private IpRateLimiter rateLimiter;

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody(required = false) ContactRequest request, HttpServletRequest httpRequest) {
        // getRemoteAddr(): dietro proxy server.forward-headers-strategy restituisce già l'IP reale
        String ip = httpRequest.getRemoteAddr();

        if (!rateLimiter.isAllowed("contact:" + ip, MAX_REQUESTS, WINDOW_MS)) {
            return ResponseEntity.status(429).body(Map.of("message",
                    "Hai inviato troppe richieste. Riprova tra un'ora oppure scrivici via email."));
        }

        // Honeypot compilato = bot: risposta di successo apparente, nessun invio
        if (request != null && request.getWebsite() != null && !request.getWebsite().isBlank()) {
            ErrorLog.logger.info("CONTACT: honeypot compilato, richiesta scartata (ip {})", ip);
            return ResponseEntity.ok(Map.of("message", "Richiesta inviata"));
        }

        String error = contactService.validate(request);
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("message", error));
        }

        return switch (contactService.send(request, ip)) {
            case SENT -> ResponseEntity.ok(Map.of("message", "Richiesta inviata"));
            case NOT_CONFIGURED -> ResponseEntity.status(503).body(Map.of("message",
                    "Il modulo di contatto non è al momento disponibile. Scrivici direttamente via email."));
            case FAILED -> ResponseEntity.status(502).body(Map.of("message",
                    "Invio non riuscito. Riprova più tardi oppure scrivici via email."));
        };
    }
}
