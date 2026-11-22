package com.modules.mainapp.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEndpointGuardTest {

    private static final String OK = "{\"success\":true,\"score\":0.9,\"action\":\"login\"}";

    private static CredentialEndpointGuard guard(String secret, String googleBody) {
        RecaptchaVerifier v = new RecaptchaVerifier(secret, 0.5, 1000, true, "", null,
                (u, f, t) -> new RecaptchaVerifier.TransportResponse(200, googleBody));
        return new CredentialEndpointGuard(v, new LoginAttemptTracker(15), 5, 5, 30, 10);
    }

    private static MockHttpServletRequest browser() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/login");
        r.setRemoteAddr("10.0.0.1");
        r.addHeader("Origin", "https://app.example.com");
        return r;
    }

    private static MockHttpServletRequest nativeApp() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/login");
        r.setRemoteAddr("10.0.0.2");
        return r;
    }

    @Test
    void disabledLeavesLoginUnchanged() {
        CredentialEndpointGuard g = guard("", OK);
        assertTrue(g.checkLogin(browser(), "a@b.it", null).allowed());
        assertTrue(g.checkSignup(browser(), null, CredentialEndpointGuard.ACTION_SIGNUP).allowed());
    }

    @Test
    void browserNeedsToken() {
        CredentialEndpointGuard g = guard("s", OK);
        CredentialEndpointGuard.Decision d = g.checkLogin(browser(), "a@b.it", null);
        assertFalse(d.allowed());
        assertEquals(403, d.status());
        assertTrue(g.checkLogin(browser(), "a@b.it", "tok").allowed());
    }

    @Test
    void nativeAppWithoutTokenUntilTooManyFailures() {
        CredentialEndpointGuard g = guard("s", OK);
        MockHttpServletRequest app = nativeApp();
        assertTrue(g.checkLogin(app, "a@b.it", null).allowed());
        for (int i = 0; i < 5; i++) g.loginFailed(app, "a@b.it");
        CredentialEndpointGuard.Decision d = g.checkLogin(app, "a@b.it", null);
        assertFalse(d.allowed());
        assertEquals(429, d.status());
        // dal sito, superando il captcha, l'utente entra comunque
        assertTrue(g.checkLogin(browser(), "a@b.it", "tok").allowed());
    }

    @Test
    void successResetsAccountCounter() {
        CredentialEndpointGuard g = guard("", OK);
        MockHttpServletRequest app = nativeApp();
        for (int i = 0; i < 10; i++) g.loginFailed(app, "A@b.it ");
        assertEquals(429, g.checkLogin(app, "a@b.it", null).status());
        g.loginSucceeded("a@b.it");
        // IP ancora sotto la soglia di blocco (30)
        assertTrue(g.checkLogin(app, "a@b.it", null).allowed());
    }

    @Test
    void ipBlockAppliesEvenWithValidCaptcha() {
        CredentialEndpointGuard g = guard("s", OK);
        MockHttpServletRequest web = browser();
        for (int i = 0; i < 30; i++) g.loginFailed(web, "user" + i);
        assertEquals(429, g.checkLogin(web, "other", "tok").status());
    }

    @Test
    void signupRequiresTokenWithMatchingAction() {
        CredentialEndpointGuard g = guard("s", "{\"success\":true,\"score\":0.9,\"action\":\"signup\"}");
        assertEquals(403, g.checkSignup(nativeApp(), null, CredentialEndpointGuard.ACTION_SIGNUP).status());
        assertTrue(g.checkSignup(browser(), "tok", CredentialEndpointGuard.ACTION_SIGNUP).allowed());
        assertFalse(g.checkSignup(browser(), "tok", CredentialEndpointGuard.ACTION_SIGNUP_STAFF).allowed());
    }
}
