package com.modules.deliverymodule.otp;

import com.modules.common.logs.errorlog.ErrorLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Configurazione OTP e protezione della modalità sviluppo.
 *
 * <p>{@code app.otp.dev-mode=true} (nessun SMS, codice fisso {@value #DEV_CODE}) è attiva SOLO se:
 * <ul>
 *   <li>{@code prelude.api-key} è vuota (con la chiave si inviano sempre SMS veri), e</li>
 *   <li>l'ambiente è locale: nessun profilo Spring {@code prod|production|staging}, {@code app.public-base-url}
 *       assente o su localhost/127.0.0.1, e {@code server.forward-headers-strategy} non impostato (in produzione il
 *       backend gira dietro Caddy con {@code framework}).</li>
 * </ul>
 * Se dev-mode è richiesta in un ambiente non locale viene <b>disattivata</b> con un ERROR nel log: il backend parte
 * (un aggiornamento non blocca il deploy) ma la verifica è "fail-closed": senza chiave Prelude l'invio risponde 503
 * e nessun codice fisso viene accettato.
 *
 * <p>La verifica del telefono è <b>attiva</b> ({@link #verificationEnabled()}) solo se gli SMS sono disponibili
 * (dev-mode o chiave Prelude) e {@code app.otp.enabled} non è {@code false}. Se è disattivata la consegna a
 * domicilio e l'asporto accettano il telefono senza codice (viene solo validato e normalizzato).
 */
@Component
public class OtpSettings {

    public static final String DEV_CODE = "123456";
    private static final Set<String> NON_LOCAL_PROFILES = Set.of("prod", "production", "staging");

    private final String apiKey;
    private final String baseUrl;
    private final boolean devMode;
    private final boolean enabled;

    @Autowired
    public OtpSettings(@Value("${prelude.api-key:}") String apiKey,
                       @Value("${prelude.base-url:https://api.prelude.dev}") String baseUrl,
                       @Value("${app.otp.dev-mode:false}") boolean devModeRequested,
                       @Value("${app.public-base-url:}") String publicBaseUrl,
                       @Value("${server.forward-headers-strategy:}") String forwardHeaders,
                       @Value("${app.otp.enabled:true}") boolean enabledRequested,
                       Environment env) {
        this(apiKey, baseUrl, devModeRequested, publicBaseUrl, forwardHeaders,
                env != null ? env.getActiveProfiles() : new String[0], enabledRequested);
    }

    public OtpSettings(String apiKey, String baseUrl, boolean devModeRequested, String publicBaseUrl,
                       String forwardHeaders, String[] activeProfiles) {
        this(apiKey, baseUrl, devModeRequested, publicBaseUrl, forwardHeaders, activeProfiles, true);
    }

    public OtpSettings(String apiKey, String baseUrl, boolean devModeRequested, String publicBaseUrl,
                       String forwardHeaders, String[] activeProfiles, boolean enabledRequested) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.prelude.dev" : baseUrl.trim();
        boolean keyPresent = !this.apiKey.isEmpty();
        String nonLocalReason = nonLocalReason(publicBaseUrl, forwardHeaders, activeProfiles);
        if (keyPresent) {
            if (devModeRequested) ErrorLog.logger.warn("OTP: app.otp.dev-mode ignorata perché prelude.api-key è impostata (SMS reali)");
            this.devMode = false;
        } else if (devModeRequested && nonLocalReason != null) {
            ErrorLog.logger.error("OTP: app.otp.dev-mode=true RIFIUTATA in un ambiente non locale ({}). "
                    + "Modalità sviluppo disattivata: imposta PRELUDE_API_KEY per inviare gli SMS.", nonLocalReason);
            this.devMode = false;
        } else {
            this.devMode = devModeRequested;
        }
        this.enabled = enabledRequested && (this.devMode || keyPresent);
        if (!enabledRequested) {
            ErrorLog.logger.warn("OTP: app.otp.enabled=false: verifica SMS del telefono disattivata (ordini senza codice)");
        } else if (this.devMode) {
            ErrorLog.logger.warn("OTP: MODALITÀ SVILUPPO attiva, nessun SMS inviato: il codice valido è " + DEV_CODE);
        } else if (!keyPresent) {
            ErrorLog.logger.warn("OTP: prelude.api-key non impostata: verifica SMS del telefono disattivata "
                    + "(consegna e asporto accettano il numero senza codice)");
        }
    }

    static String nonLocalReason(String publicBaseUrl, String forwardHeaders, String[] profiles) {
        if (profiles != null) {
            for (String p : profiles) {
                if (p != null && NON_LOCAL_PROFILES.contains(p.toLowerCase(Locale.ROOT))) return "profilo " + p;
            }
        }
        if (forwardHeaders != null && !forwardHeaders.isBlank() && !"none".equalsIgnoreCase(forwardHeaders.trim())) {
            return "server.forward-headers-strategy=" + forwardHeaders.trim();
        }
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            String host;
            try {
                host = URI.create(publicBaseUrl.trim()).getHost();
            } catch (Exception e) {
                host = null;
            }
            if (host == null || !Arrays.asList("localhost", "127.0.0.1", "::1", "[::1]").contains(host.toLowerCase(Locale.ROOT))) {
                return "app.public-base-url=" + publicBaseUrl.trim();
            }
        }
        return null;
    }

    public String apiKey() { return apiKey; }
    public String baseUrl() { return baseUrl; }
    public boolean devMode() { return devMode; }
    /** SMS reali disponibili (chiave Prelude configurata). */
    public boolean smsConfigured() { return !apiKey.isEmpty(); }
    /** Verifica del telefono attiva: SMS disponibili (dev-mode o chiave) e non disattivata con app.otp.enabled=false. */
    public boolean verificationEnabled() { return enabled; }
}
