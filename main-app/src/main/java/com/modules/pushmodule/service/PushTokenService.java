package com.modules.pushmodule.service;

import com.modules.common.dto.UserDto;
import com.modules.pushmodule.model.PushTokenJpa;
import com.modules.pushmodule.repository.PushTokenRepository;
import com.modules.servletconfiguration.model.CustomUserDetails;
import com.modules.servletconfiguration.security.AuthenticatedUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

import static com.modules.common.utilities.Constants.ROLE_ADMIN;
import static com.modules.common.utilities.Constants.ROLE_WAITER;

/** Registrazione dei token push dei dispositivi dello staff (titolare e camerieri). */
@Service
public class PushTokenService {

    static final Pattern EXPO_TOKEN = Pattern.compile("^(ExponentPushToken|ExpoPushToken)\\[[^\\]\\s]{1,200}]$");

    @Autowired
    private PushTokenRepository tokenRepository;
    @Autowired
    private AuthenticatedUserProvider auth;

    /**
     * Registra (o aggiorna) il token del dispositivo, da chiamare dopo il login e a ogni avvio dell'app.
     * Se il token era di un altro utente (logout senza deregistrazione, cambio account) passa all'utente corrente.
     *
     * @return false se ignorato: durante l'accesso come supporto il telefono del superadmin non deve ricevere
     *         le notifiche del locale
     * @throws IllegalArgumentException token non valido (400)
     * @throws ResponseStatusException 403 se l'utente non è titolare o cameriere di un locale
     */
    @Transactional
    public boolean register(String rawToken, String platform) {
        String token = requireValidToken(rawToken);
        CustomUserDetails details = auth.getCurrentDetails();
        requireStaff(details);
        if (details.isImpersonated()) return false;

        UserDto user = auth.getAuthenticatedUser();
        if (user.getIdAgency() <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nessun locale associato all'utente");
        }

        OffsetDateTime now = OffsetDateTime.now();
        PushTokenJpa entity = tokenRepository.findByToken(token).orElseGet(() -> {
            PushTokenJpa t = new PushTokenJpa();
            t.setToken(token);
            t.setCreatedAt(now);
            return t;
        });
        entity.setUserId(user.getId());
        entity.setIdAgency(user.getIdAgency());
        entity.setPlatform(normalizePlatform(platform));
        entity.setUpdatedAt(now);
        tokenRepository.save(entity);
        return true;
    }

    /** Logout: rimuove il token solo se appartiene all'utente corrente. */
    @Transactional
    public boolean unregister(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token mancante");
        }
        CustomUserDetails details = auth.getCurrentDetails();
        requireStaff(details);
        return tokenRepository.deleteByTokenAndUserId(rawToken.trim(), details.getId()) > 0;
    }

    static String requireValidToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (!EXPO_TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Token push non valido");
        }
        return token;
    }

    private static void requireStaff(CustomUserDetails details) {
        if (details == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utente non autenticato");
        }
        if (!ROLE_ADMIN.equals(details.getRole()) && !ROLE_WAITER.equals(details.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notifiche disponibili solo per lo staff del locale");
        }
    }

    static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) return null;
        String p = platform.trim().toLowerCase(Locale.ROOT);
        return p.length() > 20 ? p.substring(0, 20) : p;
    }
}
