package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.pushmodule.service.PushTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Token push dei dispositivi dello staff (app "Sala"): titolare e camerieri.
 * Il controllo del ruolo è nel service (un @PreAuthorize negato finirebbe nel GlobalExceptionHandler come 500).
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/push-tokens")
@RestController
public class PushTokenController {

    @Autowired
    private PushTokenService service;

    public record PushTokenRequest(String token, String platform) {}

    /** Registrazione/refresh del token Expo (dopo il login e a ogni avvio). data = false se ignorato (accesso come supporto). */
    @PostMapping
    public ResponseEntity<?> register(@RequestBody(required = false) PushTokenRequest req) {
        return handle(() -> service.register(req == null ? null : req.token(), req == null ? null : req.platform()));
    }

    /** Logout: data = true se il token è stato rimosso. */
    @DeleteMapping
    public ResponseEntity<?> unregister(@RequestParam(value = "token", required = false) String token) {
        return handle(() -> service.unregister(token));
    }

    static ResponseEntity<?> handle(Supplier<?> action) {
        try {
            return ResponseEntity.ok(new DataResponse<>(action.get()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Errore" : e.getReason()));
        }
    }
}
