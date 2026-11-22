package com.modules.mainapp.security;

import com.modules.mainapp.security.RecaptchaVerifier.Outcome;
import com.modules.mainapp.security.RecaptchaVerifier.TransportResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RecaptchaVerifierTest {

    private static RecaptchaVerifier verifier(String secret, boolean failOpen, String hosts, RecaptchaVerifier.Transport t) {
        return new RecaptchaVerifier(secret, 0.5, 2000, failOpen, hosts, null, t);
    }

    private static RecaptchaVerifier.Transport replying(int status, String body) {
        return (url, form, timeout) -> new TransportResponse(status, body);
    }

    @Test
    void disabledWithoutSecretAndNeverCallsGoogle() {
        RecaptchaVerifier v = verifier("  ", true, "", (u, f, t) -> { throw new AssertionError("no HTTP call expected"); });
        assertFalse(v.isEnabled());
        RecaptchaVerifier.Result r = v.verify("tok", "login", "1.2.3.4");
        assertEquals(Outcome.DISABLED, r.outcome());
        assertTrue(v.accepts(r));
    }

    @Test
    void validTokenSendsSecretTokenAndIpToSiteverify() {
        AtomicReference<String> url = new AtomicReference<>();
        AtomicReference<String> form = new AtomicReference<>();
        RecaptchaVerifier v = verifier("s3cr&t", true, "", (u, f, t) -> {
            url.set(u);
            form.set(f);
            return new TransportResponse(200, "{\"success\":true,\"score\":0.9,\"action\":\"login\",\"hostname\":\"app.example.com\"}");
        });
        RecaptchaVerifier.Result r = v.verify("abc", "login", "1.2.3.4");
        assertEquals(Outcome.VALID, r.outcome());
        assertEquals(0.9, r.score());
        assertEquals(RecaptchaVerifier.DEFAULT_VERIFY_URL, url.get());
        assertEquals("secret=s3cr%26t&response=abc&remoteip=1.2.3.4", form.get());
    }

    @Test
    void missingTokenIsRejectedWithoutCallingGoogle() {
        RecaptchaVerifier v = verifier("s", true, "", (u, f, t) -> { throw new AssertionError("no HTTP call expected"); });
        RecaptchaVerifier.Result r = v.verify(null, "login", "ip");
        assertEquals(Outcome.MISSING, r.outcome());
        assertFalse(v.accepts(r));
        assertEquals(Outcome.MISSING, v.verify("  ", "login", "ip").outcome());
        assertEquals(Outcome.INVALID, v.verify("x".repeat(RecaptchaVerifier.MAX_TOKEN_LENGTH + 1), "login", "ip").outcome());
    }

    @Test
    void lowScoreIsInvalid() {
        RecaptchaVerifier v = verifier("s", true, "", replying(200, "{\"success\":true,\"score\":0.3,\"action\":\"login\"}"));
        RecaptchaVerifier.Result r = v.verify("t", "login", null);
        assertEquals(Outcome.INVALID, r.outcome());
        assertFalse(v.accepts(r));
    }

    @Test
    void scoreAtThresholdIsValid() {
        RecaptchaVerifier v = verifier("s", true, "", replying(200, "{\"success\":true,\"score\":0.5,\"action\":\"login\"}"));
        assertEquals(Outcome.VALID, v.verify("t", "login", null).outcome());
    }

    @Test
    void wrongActionIsInvalid() {
        RecaptchaVerifier v = verifier("s", true, "", replying(200, "{\"success\":true,\"score\":0.9,\"action\":\"signup\"}"));
        assertEquals(Outcome.INVALID, v.verify("t", "login", null).outcome());
    }

    @Test
    void googleRejectionIsInvalid() {
        RecaptchaVerifier v = verifier("s", true, "", replying(200, "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}"));
        assertEquals(Outcome.INVALID, v.verify("t", "login", null).outcome());
    }

    @Test
    void hostnameAllowListIsEnforcedWhenConfigured() {
        String body = "{\"success\":true,\"score\":0.9,\"action\":\"login\",\"hostname\":\"evil.example\"}";
        assertEquals(Outcome.INVALID, verifier("s", true, "app.example.com", replying(200, body)).verify("t", "login", null).outcome());
        assertEquals(Outcome.VALID, verifier("s", true, "", replying(200, body)).verify("t", "login", null).outcome());
        assertEquals(Outcome.VALID, verifier("s", true, " App.Example.com , evil.example", replying(200, body)).verify("t", "login", null).outcome());
    }

    @Test
    void googleUnreachableFollowsFailOpenSetting() {
        RecaptchaVerifier.Transport timeout = (u, f, t) -> { throw new HttpTimeoutException("timeout"); };
        RecaptchaVerifier open = verifier("s", true, "", timeout);
        RecaptchaVerifier closed = verifier("s", false, "", timeout);
        RecaptchaVerifier.Result r = open.verify("t", "login", null);
        assertEquals(Outcome.UNAVAILABLE, r.outcome());
        assertTrue(open.accepts(r));
        assertFalse(closed.accepts(closed.verify("t", "login", null)));
    }

    @Test
    void httpErrorsAndGarbageAreUnavailable() {
        assertEquals(Outcome.UNAVAILABLE, verifier("s", true, "", replying(503, "")).verify("t", "login", null).outcome());
        assertEquals(Outcome.UNAVAILABLE, verifier("s", true, "", replying(200, "<html>")).verify("t", "login", null).outcome());
    }

    @Test
    void badSecretIsTreatedAsUnavailableNotAsClientFault() {
        RecaptchaVerifier v = verifier("s", true, "", replying(200, "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}"));
        assertEquals(Outcome.UNAVAILABLE, v.verify("t", "login", null).outcome());
    }
}
