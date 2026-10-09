package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImpersonationTokenAndPolicyTest {

    private final JwtService jwt = SuperadminTestSupport.jwtService();

    @Test
    void impersonationTokenHasLoginClaimsPlusImpAndExpiresIn60Minutes() {
        UserDto admin = SuperadminTestSupport.user(42, "pizzeria", "owner@pizzeria.it", "ROLE_ADMIN", 7);
        long before = System.currentTimeMillis();
        String token = jwt.generateImpersonationToken(admin, 1L, "boss@platform.it");

        Claims c = jwt.extractAll(token);
        assertEquals("pizzeria", c.getSubject());
        assertEquals("owner@pizzeria.it", c.get("email"));
        assertEquals(42L, jwt.extractUserID(token));
        assertTrue(JwtService.isImpersonation(c));
        assertEquals(1L, JwtService.impersonatedBy(c));
        assertEquals("boss@platform.it", JwtService.impersonatedByEmail(c));
        assertTrue(jwt.isTokenValid(token, admin));

        long exp = c.getExpiration().getTime();
        assertTrue(exp <= before + JwtService.IMPERSONATION_EXPIRATION_MS + 1000);
        assertTrue(exp >= before + JwtService.IMPERSONATION_EXPIRATION_MS - 2000);
    }

    @Test
    void normalTokenIsNotImpersonation() {
        UserDto admin = SuperadminTestSupport.user(42, "pizzeria", "owner@pizzeria.it", "ROLE_ADMIN", 7);
        Claims c = jwt.extractAll(jwt.generateToken(admin));
        assertFalse(JwtService.isImpersonation(c));
        assertNull(JwtService.impersonatedBy(c));
        assertNull(c.get(JwtService.CLAIM_IMP_BY_EMAIL));
    }

    @Test
    void blockedEndpoints() {
        assertTrue(ImpersonationPolicy.isBlocked("POST", "/api/users/changePassword"));
        assertTrue(ImpersonationPolicy.isBlocked("POST", "/api/users/changePassword/"));
        assertTrue(ImpersonationPolicy.isBlocked("post", "/api/users/updateData"));
        assertTrue(ImpersonationPolicy.isBlocked("PUT", "/api/payments/provider/stripe"));
        assertTrue(ImpersonationPolicy.isBlocked("PUT", "/api/payments/provider/sumup"));
        assertTrue(ImpersonationPolicy.isBlocked("PUT", "/api/payments/provider/active"));
        assertTrue(ImpersonationPolicy.isBlocked("DELETE", "/api/payments/provider/stripe"));
        assertTrue(ImpersonationPolicy.isBlocked("DELETE", "/api/payments/provider/sumup"));
        assertTrue(ImpersonationPolicy.isBlocked("POST", "/api/payments/15/refund"));
        assertTrue(ImpersonationPolicy.isBlocked("GET", "/api/users/closeAccount/123"));
    }

    @Test
    void allowedEndpoints() {
        assertFalse(ImpersonationPolicy.isBlocked("GET", "/api/payments/provider"));
        assertFalse(ImpersonationPolicy.isBlocked("GET", "/api/payments"));
        assertFalse(ImpersonationPolicy.isBlocked("PUT", "/api/payments/settings"));
        assertFalse(ImpersonationPolicy.isBlocked("POST", "/api/categories/addCategory"));
        assertFalse(ImpersonationPolicy.isBlocked("GET", "/api/users/getWaiters"));
        assertFalse(ImpersonationPolicy.isBlocked(null, null));
    }

    @Test
    void mutatingMethods() {
        assertTrue(ImpersonationPolicy.isMutating("POST"));
        assertTrue(ImpersonationPolicy.isMutating("put"));
        assertTrue(ImpersonationPolicy.isMutating("PATCH"));
        assertTrue(ImpersonationPolicy.isMutating("DELETE"));
        assertFalse(ImpersonationPolicy.isMutating("GET"));
        assertFalse(ImpersonationPolicy.isMutating("OPTIONS"));
    }
}
