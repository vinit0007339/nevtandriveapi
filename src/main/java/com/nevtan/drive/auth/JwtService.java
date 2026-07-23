package com.nevtan.drive.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies the Drive session token.
 *
 * <p>Identity originates in NevTan SSO; this token is what Drive hands back
 * after exchanging a verified SSO token, and is what every subsequent Drive
 * request carries. Mirrors the CylonCloud API's token scheme.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public long expirationMs() {
        return expirationMs;
    }

    public String generateToken(String userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMs);

        return Jwts.builder()
                .claims(Map.of("uid", userId, "email", email))
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public String extractUserId(String token) {
        return parseClaims(token).get("uid", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            if (keyBytes.length < 32) {
                throw new IllegalStateException(
                        "app.jwt.secret must be at least 256 bits (32 bytes) when Base64-decoded. "
                                + "Current length: " + keyBytes.length + " bytes.");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "app.jwt.secret must be a valid Base64-encoded string of at least 256 bits.",
                    exception);
        }
    }
}
