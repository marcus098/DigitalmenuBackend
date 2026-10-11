package com.modules.mainapp.controller;

import com.modules.mainapp.config.IpRateLimiter;
import com.modules.translationmodule.service.MenuTranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/** Traduzioni del menu per i clienti (pubblico, coperto da /api/public/** in SecurityConf). */
@RequestMapping("/api/public/menu-translations")
@RestController
public class PublicMenuTranslationController {

    private static final int MAX_REQUESTS = 120;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    @Autowired
    private MenuTranslationService service;
    @Autowired
    private IpRateLimiter rateLimiter;

    /** Lingue disponibili per il menu del locale (per id o nome locale). */
    @GetMapping("/{local}")
    public ResponseEntity<?> languages(@PathVariable("local") String local, HttpServletRequest http) {
        if (!rateLimiter.isAllowed("menu-i18n:" + http.getRemoteAddr(), MAX_REQUESTS, WINDOW_MS)) return tooMany();
        ResponseEntity<?> r = MenuTranslationController.handle(() -> service.publicLanguages(local));
        return cached(r, 300);
    }

    /** Testi tradotti: tipo (CATEGORY, PRODUCT, INGREDIENT) → id → campo (NAME, DESCRIPTION) → testo. */
    @GetMapping("/{local}/{lang}")
    public ResponseEntity<?> menu(@PathVariable("local") String local, @PathVariable("lang") String lang, HttpServletRequest http) {
        if (!rateLimiter.isAllowed("menu-i18n:" + http.getRemoteAddr(), MAX_REQUESTS, WINDOW_MS)) return tooMany();
        ResponseEntity<?> r = MenuTranslationController.handle(() -> service.publicMenu(local, lang));
        return cached(r, 60);
    }

    private static ResponseEntity<?> cached(ResponseEntity<?> r, int seconds) {
        if (!r.getStatusCode().is2xxSuccessful()) return r;
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(seconds)).cachePublic()).body(r.getBody());
    }

    private static ResponseEntity<?> tooMany() {
        return ResponseEntity.status(429).body(Map.of("message", "Troppe richieste, riprova tra poco"));
    }
}
