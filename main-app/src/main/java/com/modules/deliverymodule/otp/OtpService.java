package com.modules.deliverymodule.otp;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.ordermodule.exception.OrderRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Verifica del telefono via SMS (Prelude Verify) con limiti anti "SMS pumping" applicati PRIMA di chiamare Prelude
 * (una richiesta bloccata non crea una verifica a pagamento): per IP, per telefono, per locale al giorno e globali
 * al giorno; tentativi di verifica per IP e per telefono. Contatori in memoria (IpRateLimiter): con più istanze del
 * backend andrebbero spostati su uno store condiviso.
 */
@Service
public class OtpService {

    private static final long MIN = 60_000L;

    private final OtpSettings settings;
    private final PreludeVerificationClient prelude;
    private final PhoneTokenService tokens;
    private final IpRateLimiter limiter;

    @Value("${app.otp.rate-limit.send.ip.max-attempts:5}") int sendIpMax = 5;
    @Value("${app.otp.rate-limit.send.ip.window-minutes:120}") long sendIpWindow = 120;
    @Value("${app.otp.rate-limit.send.phone.max-attempts:3}") int sendPhoneMax = 3;
    @Value("${app.otp.rate-limit.send.phone.window-minutes:15}") long sendPhoneWindow = 15;
    @Value("${app.otp.rate-limit.send.venue.max-per-day:200}") int sendVenuePerDay = 200;
    @Value("${app.otp.rate-limit.send.global.max-per-day:2000}") int sendGlobalPerDay = 2000;
    @Value("${app.otp.rate-limit.check.max-attempts:5}") int checkMax = 5;
    @Value("${app.otp.rate-limit.check.window-minutes:30}") long checkWindow = 30;

    public record Verified(String phoneToken, Instant expiresAt) {}

    @Autowired
    public OtpService(OtpSettings settings, PreludeVerificationClient prelude, PhoneTokenService tokens, IpRateLimiter limiter) {
        this.settings = settings;
        this.prelude = prelude;
        this.tokens = tokens;
        this.limiter = limiter;
    }

    /**
     * Invia il codice. @throws OrderRejectedException 400 (telefono), 429 (limiti), 503/502 (servizio SMS)
     * @return telefono normalizzato E.164
     */
    public String send(long idAgency, String rawPhone, String clientIp) {
        String phone = normalize(rawPhone);
        if (!enabled()) {
            // Verifica non attiva (nessuna chiave Prelude o app.otp.enabled=false): il menu non dovrebbe chiedere il codice
            ErrorLog.logger.warn("OTP: invio richiesto per idAgency={} ma la verifica SMS non è attiva "
                    + "(prelude.api-key/PRELUDE_API_KEY assente o app.otp.enabled=false): nessun SMS inviato", idAgency);
            throw new OrderRejectedException(503, PreludeVerificationClient.MSG_UNAVAILABLE);
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        if (!limiter.isAllowed("otp-send-ip:" + ip, sendIpMax, sendIpWindow * MIN)) {
            throw new OrderRejectedException(429, "Troppi codici richiesti da questa rete. Riprova più tardi.");
        }
        if (!limiter.isAllowed("otp-send-phone:" + phone, sendPhoneMax, sendPhoneWindow * MIN)) {
            throw new OrderRejectedException(429, "Hai richiesto troppi codici per questo numero. Riprova più tardi.");
        }
        if (!limiter.isAllowed("otp-send-venue:" + idAgency, sendVenuePerDay, 24 * 60 * MIN)) {
            ErrorLog.logger.warn("OTP: limite giornaliero di SMS raggiunto per idAgency={}", idAgency);
            throw new OrderRejectedException(429, "Troppe verifiche oggi per questo locale. Riprova domani o contatta il locale.");
        }
        if (!limiter.isAllowed("otp-send-global", sendGlobalPerDay, 24 * 60 * MIN)) {
            ErrorLog.logger.error("OTP: limite globale giornaliero di SMS raggiunto ({})", sendGlobalPerDay);
            throw new OrderRejectedException(429, "Servizio di verifica momentaneamente non disponibile. Riprova più tardi.");
        }
        if (settings.devMode()) {
            ErrorLog.logger.warn("OTP (sviluppo) per {} idAgency={}: codice {}", PhoneNumbers.mask(phone), idAgency, OtpSettings.DEV_CODE);
            return phone;
        }
        prelude.sendCode(phone, ip);
        ErrorLog.logger.info("OTP inviato a {} idAgency={}", PhoneNumbers.mask(phone), idAgency);
        return phone;
    }

    /** @throws OrderRejectedException 400 (dati), 401 (codice errato), 429 (troppi tentativi) */
    public Verified verify(long idAgency, String rawPhone, String code, String clientIp) {
        String phone = normalize(rawPhone);
        String c = code == null ? "" : code.replaceAll("\\s", "");
        if (!c.matches("\\d{4,8}")) throw new OrderRejectedException(401, "Codice non valido");
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        if (!limiter.isAllowed("otp-check-ip:" + ip, checkMax * 2, checkWindow * MIN)
                || !limiter.isAllowed("otp-check-phone:" + phone, checkMax, checkWindow * MIN)) {
            throw new OrderRejectedException(429, "Troppi codici non validi. Riprova più tardi.");
        }
        if (!enabled()) throw new OrderRejectedException(503, PreludeVerificationClient.MSG_UNAVAILABLE);
        boolean ok;
        if (settings.devMode()) {
            ok = OtpSettings.DEV_CODE.equals(c);
        } else {
            if (!settings.smsConfigured()) throw new OrderRejectedException(503, PreludeVerificationClient.MSG_UNAVAILABLE);
            ok = prelude.isCodeValid(phone, c);
        }
        if (!ok) {
            ErrorLog.logger.info("OTP non valido per {} idAgency={}", PhoneNumbers.mask(phone), idAgency);
            throw new OrderRejectedException(401, "Codice non valido");
        }
        PhoneTokenService.Issued issued = tokens.issue(phone);
        return new Verified(issued.token(), issued.expiresAt());
    }

    /**
     * Verifica del telefono attiva (SMS disponibili: dev-mode o chiave Prelude, e app.otp.enabled non false).
     * Se false gli ordini a domicilio/asporto non richiedono il phoneToken.
     */
    public boolean enabled() {
        return settings.verificationEnabled();
    }

    /** Telefono verificato dal token (stesso numero, firma valida, non scaduto)? */
    public boolean isVerified(String phoneToken, String rawPhone) {
        String phone;
        try {
            phone = PhoneNumbers.normalize(rawPhone);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String fromToken = tokens.verify(phoneToken);
        return fromToken != null && fromToken.equals(phone);
    }

    private static String normalize(String raw) {
        try {
            return PhoneNumbers.normalize(raw);
        } catch (IllegalArgumentException e) {
            throw new OrderRejectedException(400, e.getMessage());
        }
    }
}
