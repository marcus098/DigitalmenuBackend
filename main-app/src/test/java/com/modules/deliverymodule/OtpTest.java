package com.modules.deliverymodule;

import com.modules.deliverymodule.otp.*;
import com.modules.mainapp.config.IpRateLimiter;
import com.modules.ordermodule.exception.OrderRejectedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifica SMS: normalizzazione, modalità sviluppo e sua protezione, limiti anti SMS pumping, phoneToken. */
class OtpTest {

    private static final String JWT = "0d144323f0a05311a62c8ca4b4db1a06af91d779c4841c3a0d165c92807d3897";

    private static OtpSettings devSettings() {
        return new OtpSettings("", null, true, "", "", new String[0]);
    }

    private static OtpService service(OtpSettings settings, PreludeVerificationClient prelude) {
        return new OtpService(settings, prelude, new PhoneTokenService("", JWT), new IpRateLimiter());
    }

    // ── Telefono ─────────────────────────────────────────────────────────────

    @Test
    void phoneNormalizedToE164() {
        assertEquals("+393331234567", PhoneNumbers.normalize("333 123 4567"));
        assertEquals("+393331234567", PhoneNumbers.normalize("0039 333-123.4567"));
        assertEquals("+393331234567", PhoneNumbers.normalize("+39 (333) 1234567"));
        assertEquals("+390301234567", PhoneNumbers.normalize("030 1234567"));
        assertEquals("+442079460000", PhoneNumbers.normalize("+44 20 7946 0000"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("123"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("+39 999 1234567"));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("abc"));
        assertEquals("***4567", PhoneNumbers.mask("+393331234567"));
    }

    // ── Modalità sviluppo ────────────────────────────────────────────────────

    @Test
    void devModeAcceptsOnlyFixedCodeAndSendsNoSms() {
        PreludeVerificationClient prelude = mock(PreludeVerificationClient.class);
        OtpService otp = service(devSettings(), prelude);
        assertEquals("+393331234567", otp.send(3, "3331234567", "1.1.1.1"));
        assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "654321", "1.1.1.1"));
        OtpService.Verified v = otp.verify(3, "3331234567", "123456", "1.1.1.1");
        assertTrue(otp.isVerified(v.phoneToken(), "+39 333 1234567"));
        verifyNoInteractions(prelude);
    }

    @Test
    void wrongCodeIs401() {
        OtpService otp = service(devSettings(), mock(PreludeVerificationClient.class));
        OrderRejectedException e = assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "000000", "1.1.1.1"));
        assertEquals(401, e.getStatus());
        assertEquals("Codice non valido", e.getMessage());
    }

    @Test
    void devModeRefusedOutsideLocalEnvironment() {
        assertFalse(new OtpSettings("", null, true, "https://api.restaurantflow.it", "", new String[0]).devMode());
        assertFalse(new OtpSettings("", null, true, "", "framework", new String[0]).devMode());
        assertFalse(new OtpSettings("", null, true, "", "", new String[]{"prod"}).devMode());
        assertTrue(new OtpSettings("", null, true, "http://localhost:8085", "", new String[]{"dev"}).devMode());
        // con la chiave Prelude si inviano sempre SMS veri
        assertFalse(new OtpSettings("key", null, true, "", "", new String[0]).devMode());
    }

    @Test
    void withoutKeyAndDevModeSendIs503AndFixedCodeRejected() {
        OtpSettings prod = new OtpSettings("", null, true, "https://api.example.com", "framework", new String[0]);
        OtpService otp = service(prod, mock(PreludeVerificationClient.class));
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> otp.send(3, "3331234567", "1.1.1.1")).getStatus());
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "123456", "1.1.1.1")).getStatus());
    }

    @Test
    void verificationEnabledOnlyWhenSmsAvailable() {
        // dev-mode locale o chiave Prelude → attiva
        assertTrue(service(devSettings(), mock(PreludeVerificationClient.class)).enabled());
        assertTrue(service(new OtpSettings("key", null, false, "", "", new String[0]), mock(PreludeVerificationClient.class)).enabled());
        // nessuna chiave e niente dev-mode (produzione) → non attiva
        assertFalse(new OtpSettings("", null, false, "", "", new String[0]).verificationEnabled());
        assertFalse(new OtpSettings("", null, true, "https://api.example.com", "framework", new String[0]).verificationEnabled());
        // app.otp.enabled=false spegne la verifica anche con la chiave
        assertFalse(new OtpSettings("key", null, false, "", "", new String[0], false).verificationEnabled());
        assertFalse(new OtpSettings("", null, true, "", "", new String[0], false).verificationEnabled());
    }

    @Test
    void forcedOffSendsNoSmsEvenWithKey() {
        PreludeVerificationClient prelude = mock(PreludeVerificationClient.class);
        OtpService otp = service(new OtpSettings("key", null, false, "", "", new String[0], false), prelude);
        assertFalse(otp.enabled());
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> otp.send(3, "3331234567", "1.1.1.1")).getStatus());
        assertEquals(503, assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "123456", "1.1.1.1")).getStatus());
        verifyNoInteractions(prelude);
    }

    @Test
    void realModeDelegatesToPrelude() {
        PreludeVerificationClient prelude = mock(PreludeVerificationClient.class);
        when(prelude.isCodeValid("+393331234567", "987654")).thenReturn(true);
        OtpService otp = service(new OtpSettings("key", null, false, "", "", new String[0]), prelude);
        otp.send(3, "3331234567", "1.1.1.1");
        verify(prelude).sendCode("+393331234567", "1.1.1.1");
        assertNotNull(otp.verify(3, "3331234567", "987654", "1.1.1.1").phoneToken());
        assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "123456", "1.1.1.1"));
    }

    // ── Limiti ───────────────────────────────────────────────────────────────

    @Test
    void sendLimitedPerPhone() {
        OtpService otp = service(devSettings(), mock(PreludeVerificationClient.class));
        for (int i = 0; i < 3; i++) otp.send(3, "3331234567", "10.0.0." + i);
        OrderRejectedException e = assertThrows(OrderRejectedException.class, () -> otp.send(3, "3331234567", "10.0.0.9"));
        assertEquals(429, e.getStatus());
    }

    @Test
    void sendLimitedPerIp() {
        OtpService otp = service(devSettings(), mock(PreludeVerificationClient.class));
        for (int i = 0; i < 5; i++) otp.send(3, "333123456" + i, "2.2.2.2");
        assertEquals(429, assertThrows(OrderRejectedException.class, () -> otp.send(3, "3331234569", "2.2.2.2")).getStatus());
    }

    @Test
    void sendLimitedPerVenuePerDayAndPreludeNotCalled() {
        PreludeVerificationClient prelude = mock(PreludeVerificationClient.class);
        OtpService otp = service(new OtpSettings("key", null, false, "", "", new String[0]), prelude);
        org.springframework.test.util.ReflectionTestUtils.setField(otp, "sendVenuePerDay", 2);
        otp.send(7, "3330000001", "3.3.3.1");
        otp.send(7, "3330000002", "3.3.3.2");
        assertEquals(429, assertThrows(OrderRejectedException.class, () -> otp.send(7, "3330000003", "3.3.3.3")).getStatus());
        verify(prelude, times(2)).sendCode(anyString(), anyString());
        otp.send(8, "3330000003", "3.3.3.3"); // altro locale: ok
    }

    @Test
    void checkAttemptsLimited() {
        OtpService otp = service(devSettings(), mock(PreludeVerificationClient.class));
        for (int i = 0; i < 5; i++) {
            assertEquals(401, assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "111111", "4.4.4.4")).getStatus());
        }
        // anche il codice giusto ora è bloccato
        assertEquals(429, assertThrows(OrderRejectedException.class, () -> otp.verify(3, "3331234567", "123456", "4.4.4.4")).getStatus());
    }

    // ── phoneToken ───────────────────────────────────────────────────────────

    @Test
    void tokenValidFor90DaysThenExpires() {
        Instant t0 = Instant.parse("2026-11-15T10:00:00Z");
        PhoneTokenService issuer = new PhoneTokenService("", JWT, Clock.fixed(t0, ZoneOffset.UTC));
        PhoneTokenService.Issued issued = issuer.issue("+393331234567");
        assertEquals(t0.plus(Duration.ofDays(90)), issued.expiresAt());

        PhoneTokenService day89 = new PhoneTokenService("", JWT, Clock.fixed(t0.plus(Duration.ofDays(89)), ZoneOffset.UTC));
        assertEquals("+393331234567", day89.verify(issued.token()));
        PhoneTokenService day90 = new PhoneTokenService("", JWT, Clock.fixed(t0.plus(Duration.ofDays(90)), ZoneOffset.UTC));
        assertNull(day90.verify(issued.token()));
    }

    @Test
    void tamperedTokenRejected() {
        PhoneTokenService svc = new PhoneTokenService("", JWT);
        String token = svc.issue("+393331234567").token();
        String[] p = token.split("\\.");
        // telefono cambiato, firma originale
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("+393339999999|" + (Instant.now().getEpochSecond() + 3600)).getBytes());
        assertNull(svc.verify(p[0] + "." + forgedPayload + "." + p[2]));
        // firma alterata
        char last = p[2].charAt(p[2].length() - 1);
        assertNull(svc.verify(p[0] + "." + p[1] + "." + p[2].substring(0, p[2].length() - 1) + (last == 'A' ? 'B' : 'A')));
        assertNull(svc.verify("garbage"));
        assertNull(svc.verify(null));
        // firmato con un altro segreto
        assertNull(new PhoneTokenService("altro-segreto", JWT).verify(token));
        assertEquals("+393331234567", svc.verify(token));
    }

    @Test
    void tokenBoundToPhone() {
        OtpService otp = service(devSettings(), mock(PreludeVerificationClient.class));
        String token = otp.verify(3, "3331234567", "123456", "5.5.5.5").phoneToken();
        assertTrue(otp.isVerified(token, "+393331234567"));
        assertFalse(otp.isVerified(token, "3339999999"));
        assertFalse(otp.isVerified(null, "3331234567"));
    }
}
