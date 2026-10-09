package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

public final class SuperadminTestSupport {

    public static final long JWT_EXPIRATION = 86_400_000L;
    public static final long JWT_EXPIRATION_CHECK = 26_400_000L;

    private SuperadminTestSupport() {
    }

    public static JwtService jwtService() {
        byte[] k = new byte[64];
        new SecureRandom().nextBytes(k);
        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "key", Base64.getEncoder().encodeToString(k));
        ReflectionTestUtils.setField(jwt, "jwt_expiration", JWT_EXPIRATION);
        ReflectionTestUtils.setField(jwt, "jwt_expiration_check", JWT_EXPIRATION_CHECK);
        return jwt;
    }

    public static UserDto user(long id, String username, String email, String role, long idAgency) {
        return new UserDto(id, username, email, role, null, idAgency, true, false, null, null, null, "Mario", "Rossi");
    }
}
