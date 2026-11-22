package com.modules.mainapp.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Protezione degli endpoint pubblici con credenziali (login, registrazione locale, registrazione staff).
 *
 * <p><b>Login</b> ({@code POST /api/login}, azione reCAPTCHA {@code "login"}):
 * <ul>
 *     <li>richieste da browser (header Origin, Referer o Sec-Fetch-Mode presenti, cioè la dashboard web): token
 *     reCAPTCHA sempre obbligatorio quando la verifica è attiva;</li>
 *     <li>richieste senza segnali da browser (app nativa "DineUp Sala", che non può eseguire reCAPTCHA web): accettate
 *     senza token finché l'IP o l'account non accumulano troppi tentativi falliti; poi il token diventa obbligatorio
 *     (in pratica: dall'app si riprova a fine finestra, dal sito si entra subito superando il captcha);</li>
 *     <li>sempre: blocco temporaneo (429) per IP dopo troppi fallimenti, e per account dopo troppi fallimenti se la
 *     richiesta non ha un captcha valido (chi supera il captcha non viene bloccato da un attaccante che martella
 *     lo stesso account da altri IP).</li>
 * </ul>
 * Il token viaggia nel corpo della richiesta, campo {@code recaptchaToken} (JSON o multipart): niente header custom,
 * così il preflight CORS del proxy (Content-Type, Authorization) non cambia.
 *
 * <p><b>Registrazioni</b> ({@code /api/signupAgency}, {@code /api/signupWaiter}): l'app non le usa, quindi con la
 * verifica attiva il token è sempre obbligatorio (azioni {@code "signup"} e {@code "signup_staff"}).
 */
@Component
public class CredentialEndpointGuard {

    public static final String ACTION_LOGIN = "login";
    public static final String ACTION_SIGNUP = "signup";
    public static final String ACTION_SIGNUP_STAFF = "signup_staff";

    static final String MSG_CAPTCHA_FAILED =
            "Verifica di sicurezza non superata. Ricarica la pagina e riprova.";
    static final String MSG_TOO_MANY_IP =
            "Troppi tentativi di accesso non riusciti da questa rete. Riprova tra %d minuti.";
    static final String MSG_TOO_MANY_ACCOUNT =
            "Troppi tentativi di accesso non riusciti per questo account. Riprova tra %d minuti oppure accedi dal sito web.";

    /** Esito: allowed oppure status HTTP + messaggio per l'utente. */
    public record Decision(boolean allowed, int status, String message) {
        static final Decision ALLOW = new Decision(true, 200, null);
        static Decision deny(int status, String message) { return new Decision(false, status, message); }

        public Map<String, Object> body() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", status);
            m.put("message", message);
            return m;
        }
    }

    private final RecaptchaVerifier verifier;
    private final LoginAttemptTracker tracker;
    private final int captchaAfterIpFailures;
    private final int captchaAfterAccountFailures;
    private final int blockAfterIpFailures;
    private final int blockAfterAccountFailures;

    public CredentialEndpointGuard(RecaptchaVerifier verifier, LoginAttemptTracker tracker,
                                   @Value("${app.login.attempts.captcha-after-ip-failures:5}") int captchaAfterIpFailures,
                                   @Value("${app.login.attempts.captcha-after-account-failures:5}") int captchaAfterAccountFailures,
                                   @Value("${app.login.attempts.block-after-ip-failures:30}") int blockAfterIpFailures,
                                   @Value("${app.login.attempts.block-after-account-failures:10}") int blockAfterAccountFailures) {
        this.verifier = verifier;
        this.tracker = tracker;
        this.captchaAfterIpFailures = captchaAfterIpFailures;
        this.captchaAfterAccountFailures = captchaAfterAccountFailures;
        this.blockAfterIpFailures = blockAfterIpFailures;
        this.blockAfterAccountFailures = blockAfterAccountFailures;
    }

    public Decision checkLogin(HttpServletRequest request, String account, String recaptchaToken) {
        String ip = clientIp(request);
        String ipKey = ipKey(ip);
        String accKey = accountKey(account);
        int ipFailures = tracker.failures(ipKey);
        int accFailures = accKey == null ? 0 : tracker.failures(accKey);
        long minutes = tracker.windowMinutes();

        if (blockAfterIpFailures > 0 && ipFailures >= blockAfterIpFailures) {
            return Decision.deny(429, String.format(MSG_TOO_MANY_IP, minutes));
        }

        boolean browser = isBrowserRequest(request);
        boolean hasToken = recaptchaToken != null && !recaptchaToken.isBlank();
        boolean captchaRequired = verifier.isEnabled() && (browser
                || (captchaAfterIpFailures > 0 && ipFailures >= captchaAfterIpFailures)
                || (captchaAfterAccountFailures > 0 && accFailures >= captchaAfterAccountFailures));

        boolean human = false;
        if (verifier.isEnabled() && (captchaRequired || hasToken)) {
            RecaptchaVerifier.Result result = verifier.verify(recaptchaToken, ACTION_LOGIN, ip);
            human = result.outcome() == RecaptchaVerifier.Outcome.VALID;
            if (captchaRequired && !verifier.accepts(result)) {
                tracker.recordFailure(ipKey);
                if (!browser && result.outcome() == RecaptchaVerifier.Outcome.MISSING) {
                    // App nativa oltre la soglia: non può mostrare il captcha, le diciamo di aspettare
                    return Decision.deny(429, String.format(MSG_TOO_MANY_ACCOUNT, minutes));
                }
                return Decision.deny(403, MSG_CAPTCHA_FAILED);
            }
        }

        if (!human && blockAfterAccountFailures > 0 && accFailures >= blockAfterAccountFailures) {
            return Decision.deny(429, String.format(MSG_TOO_MANY_ACCOUNT, minutes));
        }
        return Decision.ALLOW;
    }

    /** Credenziali errate (o utente inesistente): conta per IP e per account. */
    public void loginFailed(HttpServletRequest request, String account) {
        tracker.recordFailure(ipKey(clientIp(request)));
        String accKey = accountKey(account);
        if (accKey != null) tracker.recordFailure(accKey);
    }

    /** Password corretta: azzera il contatore dell'account (quello dell'IP scade da solo). */
    public void loginSucceeded(String account) {
        String accKey = accountKey(account);
        if (accKey != null) tracker.reset(accKey);
    }

    public Decision checkSignup(HttpServletRequest request, String recaptchaToken, String action) {
        if (!verifier.isEnabled()) return Decision.ALLOW;
        RecaptchaVerifier.Result result = verifier.verify(recaptchaToken, action, clientIp(request));
        return verifier.accepts(result) ? Decision.ALLOW : Decision.deny(403, MSG_CAPTCHA_FAILED);
    }

    /** I browser mandano sempre Origin sui POST (e Sec-Fetch-*); l'app React Native su Android/iOS no. */
    static boolean isBrowserRequest(HttpServletRequest request) {
        return notBlank(request.getHeader("Origin"))
                || notBlank(request.getHeader("Sec-Fetch-Mode"))
                || notBlank(request.getHeader("Referer"));
    }

    /** remoteAddr, non X-Forwarded-For: dietro proxy lo riscrive server.forward-headers-strategy. */
    private static String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private static String ipKey(String ip) {
        return "ip:" + ip;
    }

    private static String accountKey(String account) {
        if (account == null || account.isBlank()) return null;
        String norm = account.trim().toLowerCase(Locale.ROOT);
        if (norm.length() > 254) norm = norm.substring(0, 254);
        return "acc:" + norm;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
