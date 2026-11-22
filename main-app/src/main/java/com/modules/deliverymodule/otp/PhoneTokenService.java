package com.modules.deliverymodule.otp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * phoneToken: prova firmata (HMAC-SHA256) che il telefono è stato verificato via SMS.
 * Formato {@code pt1.<base64url("E164|scadenzaEpochSec")>.<base64url(hmac)>}, validità 90 giorni.
 *
 * Segreto: {@code app.otp.token-secret} se impostato, altrimenti derivato dalla chiave JWT
 * ({@code HMAC(jwt.application.key, "restaurantflow-phone-token-v1")}: chiave distinta, stesso segreto da custodire).
 * Cambiare il segreto invalida tutti i token (i clienti rifanno la verifica SMS).
 */
@Component
public class PhoneTokenService {

    public static final Duration VALIDITY = Duration.ofDays(90);
    private static final String PREFIX = "pt1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] key;
    private final Clock clock;

    public record Issued(String token, Instant expiresAt) {}

    @Autowired
    public PhoneTokenService(@Value("${app.otp.token-secret:}") String tokenSecret,
                             @Value("${jwt.application.key}") String jwtKey) {
        this(tokenSecret, jwtKey, Clock.systemUTC());
    }

    public PhoneTokenService(String tokenSecret, String jwtKey, Clock clock) {
        this.clock = clock;
        if (tokenSecret != null && !tokenSecret.isBlank()) {
            this.key = tokenSecret.trim().getBytes(StandardCharsets.UTF_8);
        } else {
            if (jwtKey == null || jwtKey.isBlank()) throw new IllegalStateException("Segreto per i phoneToken mancante");
            this.key = hmac(jwtKey.getBytes(StandardCharsets.UTF_8), "restaurantflow-phone-token-v1".getBytes(StandardCharsets.UTF_8));
        }
    }

    public Issued issue(String e164Phone) {
        Instant exp = clock.instant().plus(VALIDITY);
        String payload = e164Phone + "|" + exp.getEpochSecond();
        String p = B64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sig = B64.encodeToString(hmac(key, (PREFIX + "." + p).getBytes(StandardCharsets.UTF_8)));
        return new Issued(PREFIX + "." + p + "." + sig, exp);
    }

    /** Telefono E.164 del token se firma valida e non scaduto, altrimenti null. */
    public String verify(String token) {
        if (token == null || token.length() > 512) return null;
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) return null;
        try {
            byte[] expected = hmac(key, (PREFIX + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, B64D.decode(parts[2]))) return null;
            String payload = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
            int sep = payload.lastIndexOf('|');
            if (sep <= 0) return null;
            long exp = Long.parseLong(payload.substring(sep + 1));
            if (clock.instant().getEpochSecond() >= exp) return null;
            return payload.substring(0, sep);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC non disponibile", e);
        }
    }
}
