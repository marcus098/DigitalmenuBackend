package com.modules.servletconfiguration.security;

import com.modules.common.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {
    @Value("${jwt.application.key}")
    private String key;

    @Value("${jwt.application.jwt_expiration}")
    private long jwt_expiration;

    @Value("${jwt.application.jwt_expiration_check}")
    private long jwt_expiration_check;

    /** Claim dei token di impersonazione (superadmin che entra come un locale). */
    public static final String CLAIM_IMP = "imp";
    public static final String CLAIM_IMP_BY = "impBy";
    public static final String CLAIM_IMP_BY_EMAIL = "impByEmail";
    /** Durata fissa dei token di impersonazione: mai rinnovati da /api/user/check. */
    public static final long IMPERSONATION_EXPIRATION_MS = 60L * 60 * 1000;

    public String generateToken(UserDto user) {
        return generateToken(user, baseClaims(user));
    }

    private static Map<String, Object> baseClaims(UserDto user) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("email", user.getEmail());
        extra.put("id", user.getId());
        return extra;
    }

    /**
     * Token per l'utente {@code user} (admin del locale) emesso da un superadmin: stessi claim del login
     * (email, id, subject=username, quindi valido anche per webflux/SSE) + imp/impBy/impByEmail, scadenza 60 minuti.
     */
    public String generateImpersonationToken(UserDto user, long superadminId, String superadminEmail) {
        Map<String, Object> extra = baseClaims(user);
        extra.put(CLAIM_IMP, true);
        extra.put(CLAIM_IMP_BY, superadminId);
        extra.put(CLAIM_IMP_BY_EMAIL, superadminEmail);
        return builToken(user, extra, IMPERSONATION_EXPIRATION_MS);
    }

    public static boolean isImpersonation(Claims claims) {
        return claims != null && Boolean.TRUE.equals(claims.get(CLAIM_IMP));
    }

    /** id del superadmin che ha emesso il token di impersonazione, null se assente/non numerico. */
    public static Long impersonatedBy(Claims claims) {
        Object v = claims == null ? null : claims.get(CLAIM_IMP_BY);
        return v instanceof Number n ? n.longValue() : null;
    }

    public static String impersonatedByEmail(Claims claims) {
        Object v = claims == null ? null : claims.get(CLAIM_IMP_BY_EMAIL);
        return v != null ? v.toString() : null;
    }

    public String generateToken(UserDto user, Map<String, Object> extra) {
        return builToken(user, extra, jwt_expiration);
    }

    public String builToken(UserDto user, Map<String, Object> extra, long expiration) {
        return Jwts.builder()
                .setClaims(extra)
                .setSubject(user.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey(), SignatureAlgorithm.HS256).compact();
    }

    public Key getKey() {
        byte[] jbytes = Decoders.BASE64.decode(key);
        return Keys.hmacShaKeyFor(jbytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        Claims claims = extractAll(token);
        return claims.get("email").toString();
    }

    public Long extractUserID(String token) {
        Claims claims = extractAll(token);
        Object id = claims.get("id");
        if (id instanceof Integer) {
            return ((Integer) id).longValue();
        } else if (id instanceof Long) {
            return (Long) id;
        } else {
            throw new IllegalArgumentException("ID is not of type Integer or Long");
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAll(token);
        return resolver.apply(claims);
    }

    public Claims extractAll(String token) {
        byte[] jbytes = Decoders.BASE64.decode(key);
        SecretKey secretKey = Keys.hmacShaKeyFor(jbytes);
        //return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    //public boolean isTokenValid(String token, UserDetails userDetails) {
    //    String username = extractUsername(token);
    //    return username.equals(userDetails.getUsername()) && !isExpired(token);
    //}

    public boolean isTokenValid(String token, UserDto user) {
        String username = extractUsername(token);
        return username.equals(user.getUsername()) && !isExpired(token);
    }

    public boolean isExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isTokenExpiringSoon(String token) {
        Claims claims = extractAll(token);
        Date expirationDate = claims.getExpiration();
        long timeToExpire = expirationDate.getTime() - System.currentTimeMillis();
        long twoDaysInMillis = jwt_expiration_check;
        return timeToExpire < twoDaysInMillis;
    }

}
