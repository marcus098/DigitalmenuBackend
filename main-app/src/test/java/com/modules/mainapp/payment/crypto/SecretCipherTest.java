package com.modules.mainapp.payment.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private static String randomKey() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void roundTripWithRandomIv() {
        SecretCipher cipher = new SecretCipher(randomKey());
        assertTrue(cipher.isAvailable());
        String secret = "sk_test_51AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";

        String a = cipher.encrypt(secret);
        String b = cipher.encrypt(secret);

        assertTrue(a.startsWith("v1:"));
        assertFalse(a.contains(secret));
        assertNotEquals(a, b, "IV casuale: due cifrature dello stesso testo devono differire");
        assertEquals(secret, cipher.decrypt(a));
        assertEquals(secret, cipher.decrypt(b));
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        SecretCipher cipher = new SecretCipher(randomKey());
        String enc = cipher.encrypt("whsec_abc123");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 5] ^= 0x01; // modifica un byte del ciphertext/tag
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v2:" + enc.substring(3)));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:AAAA"));
    }

    @Test
    void otherKeyCannotDecrypt() {
        String enc = new SecretCipher(randomKey()).encrypt("sup_sk_123");
        assertThrows(IllegalStateException.class, () -> new SecretCipher(randomKey()).decrypt(enc));
    }

    @Test
    void missingOrInvalidKeyDisablesCipherWith503() {
        for (String key : new String[]{"", null, "REPLACE_ME", "not-base64!!", Base64.getEncoder().encodeToString(new byte[16])}) {
            SecretCipher cipher = new SecretCipher(key);
            assertFalse(cipher.isAvailable());
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> cipher.encrypt("x"));
            assertEquals(503, e.getStatusCode().value());
            assertEquals(SecretCipher.NOT_CONFIGURED, e.getReason());
        }
    }
}
