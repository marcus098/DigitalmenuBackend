package com.modules.webfluxmodule.services;

import com.modules.webfluxmodule.models.db.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    @Value("${jwt.application.key}")
    private String key;

    @Value("${jwt.application.jwt_expiration}")
    private long jwt_expiration;

    @Value("${jwt.application.jwt_expiration_check}")
    private long jwt_expiration_check;

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
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(token);
    }

    public boolean isTokenValid(String token, Users user) {
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
