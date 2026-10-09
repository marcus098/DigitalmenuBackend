package com.modules.mainapp.payment.crypto;

import com.modules.common.logs.errorlog.ErrorLog;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifratura dei segreti dei locali (chiavi API Stripe/SumUp, webhook secret) con AES-256-GCM.
 * <p>
 * Formato: {@code v1:} + base64(iv[12] || ciphertext || tag[16]). IV casuale per ogni cifratura.
 * Chiave: proprietà {@code app.secrets.encryption-key} (env {@code APP_SECRETS_ENCRYPTION_KEY}), base64 di 32 byte.
 * <p>
 * L'applicazione parte anche senza chiave (errore nel log): in quel caso {@link #isAvailable()} è false, i segreti
 * non si possono salvare (503) né leggere e i pagamenti online risultano disattivati.
 */
@Component
public class SecretCipher {

    public static final String PREFIX = "v1:";
    public static final String NOT_CONFIGURED = "Cifratura non configurata sul server";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final String configError;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.secrets.encryption-key:}") String base64Key) {
        SecretKey k = null;
        String err = null;
        if (base64Key == null || base64Key.isBlank() || base64Key.contains("REPLACE")) {
            err = "app.secrets.encryption-key (APP_SECRETS_ENCRYPTION_KEY) non impostata";
        } else {
            try {
                byte[] raw = Base64.getDecoder().decode(base64Key.trim());
                if (raw.length != 32) {
                    err = "app.secrets.encryption-key deve essere il base64 di 32 byte (trovati " + raw.length + ")";
                } else {
                    k = new SecretKeySpec(raw, "AES");
                }
            } catch (IllegalArgumentException e) {
                err = "app.secrets.encryption-key non è base64 valido";
            }
        }
        this.key = k;
        this.configError = err;
    }

    @PostConstruct
    void logStatus() {
        if (configError != null) {
            ErrorLog.logger.error("!!! SEGRETI: {}. I locali non potranno salvare le chiavi di pagamento e i "
                    + "pagamenti online sono DISATTIVATI. Genera la chiave con: openssl rand -base64 32 !!!", configError);
        }
    }

    public boolean isAvailable() {
        return key != null;
    }

    /** @throws ResponseStatusException 503 se la chiave di cifratura non è configurata */
    public void requireAvailable() {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, NOT_CONFIGURED);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        requireAvailable();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct);
            return PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cifratura fallita", e);
        }
    }

    /**
     * @throws IllegalStateException se il valore è malformato, manomesso o cifrato con un'altra chiave
     */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        requireAvailable();
        if (!encrypted.startsWith(PREFIX)) {
            throw new IllegalStateException("Formato segreto cifrato non supportato");
        }
        try {
            byte[] all = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (all.length < IV_BYTES + TAG_BITS / 8) throw new IllegalStateException("Segreto cifrato troppo corto");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] pt = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Impossibile decifrare il segreto (chiave errata o dato manomesso)", e);
        }
    }
}
