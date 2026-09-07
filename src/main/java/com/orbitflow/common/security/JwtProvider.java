package com.orbitflow.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final long accessTtlMinutes;
    private final long refreshTtlDays;

    public JwtProvider(
            @Value("${orbitflow.jwt.secret:change-me-to-a-256-bit-secret-change-me-to-a-256-bit-secret-0123456789}") String secret,
            @Value("${orbitflow.jwt.access-token-ttl-minutes:30}") long accessTtlMinutes,
            @Value("${orbitflow.jwt.refresh-token-ttl-days:14}") long refreshTtlDays) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // Ensure >= 256 bits; pad deterministically if short (dev/test convenience)
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            for (int i = 0; i < 32; i++) padded[i] = bytes[i % bytes.length];
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlMinutes = accessTtlMinutes;
        this.refreshTtlDays = refreshTtlDays;
    }

    public String generateAccessToken(UUID userId, String email, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtlDays * 24 * 3600)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID userId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public boolean isAccessToken(String token) {
        try {
            return "access".equals(parse(token).get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
