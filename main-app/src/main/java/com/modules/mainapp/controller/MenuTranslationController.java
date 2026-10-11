package com.modules.mainapp.controller;

import com.modules.common.responses.DataResponse;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import com.modules.translationmodule.service.MenuTranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Lingue del menu e traduzioni (dashboard del locale). */
@CrossOrigin(origins = "*")
@RequestMapping("/api/translations")
@RestController
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class MenuTranslationController {

    @Autowired
    private MenuTranslationService service;
    @Autowired
    private AuthenticatedUserProvider auth;

    public record SettingsRequest(List<String> languages, Boolean autoTranslate) {}
    public record StartRequest(List<String> languages) {}
    public record ManualRequest(String lang, String type, long id, String field, String text) {}

    /** Impostazioni, avanzamento per lingua e stato del lavoro in corso. */
    @GetMapping
    public ResponseEntity<?> overview() {
        return handle(() -> service.overview(auth.getAgencyId()));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest req) {
        return handle(() -> service.updateSettings(auth.getAgencyId(), req.languages(), Boolean.TRUE.equals(req.autoTranslate())));
    }

    /** Testi italiani con la traduzione nella lingua scelta (per rivederli e correggerli). */
    @GetMapping("/{lang}")
    public ResponseEntity<?> rows(@PathVariable("lang") String lang) {
        return handle(() -> service.rows(auth.getAgencyId(), lang));
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody(required = false) StartRequest req) {
        return handle(() -> service.start(auth.getAgencyId(), req == null ? null : req.languages()));
    }

    @PutMapping("/manual")
    public ResponseEntity<?> manual(@RequestBody ManualRequest req) {
        return handle(() -> service.saveManual(auth.getAgencyId(), req.lang(), req.type(), req.id(), req.field(), req.text()));
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
