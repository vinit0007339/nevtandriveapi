package com.nevtan.drive.service;

import com.nevtan.drive.auth.JwtService;
import com.nevtan.drive.dto.AuthSessionResponse;
import com.nevtan.drive.dto.AuthUserResponse;
import com.nevtan.drive.entity.DriveRefreshToken;
import com.nevtan.drive.entity.DriveUser;
import com.nevtan.drive.exception.AuthenticationFailedException;
import com.nevtan.drive.repository.DriveRefreshTokenRepository;
import com.nevtan.drive.repository.DriveUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Exchanges a NevTan SSO access token for a Drive session, mirroring the
 * CylonCloud API's flow: introspect the SSO token, auto-provision the user on
 * first sign-in, then issue Drive's own access and refresh tokens.
 *
 * <p>Sign-up, email verification, and passwords are owned by the SSO. Drive
 * never sees a credential.
 */
@Service
@RequiredArgsConstructor
public class DriveAuthService {

    private static final Logger log = LoggerFactory.getLogger(DriveAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DriveUserRepository userRepository;
    private final DriveRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final RestClient.Builder restClientBuilder;

    @Value("${sso.introspect-url}")
    private String ssoIntrospectUrl;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthSessionResponse ssoLogin(String ssoToken) {
        if (ssoToken == null || ssoToken.isBlank()) {
            throw new AuthenticationFailedException("SSO token is required");
        }

        Map<String, Object> claims = introspect(ssoToken);
        if (claims == null || !Boolean.TRUE.equals(claims.get("active"))) {
            throw new AuthenticationFailedException("Invalid or expired SSO token");
        }

        String email = normalizeEmail(asString(claims.get("email")));
        if (email == null) {
            throw new AuthenticationFailedException("SSO token missing email claim");
        }

        DriveUser user = findOrProvision(email, claims);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("event=drive_sso_login userId={} provisioned={}",
                user.getId(), user.getCreatedAt().equals(user.getUpdatedAt()));

        return issueSession(user);
    }

    @Transactional
    public AuthSessionResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthenticationFailedException("Refresh token is required");
        }

        DriveRefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        // Rotate on every use: the presented token is consumed either way, so a
        // stolen token cannot be replayed after the legitimate client refreshes.
        refreshTokenRepository.delete(stored);
        if (stored.isExpired()) {
            throw new AuthenticationFailedException("Refresh token has expired");
        }

        DriveUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists"));

        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByTokenHash(hash(rawRefreshToken));
    }

    public AuthUserResponse describe(String email) {
        DriveUser user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists"));
        return toUserResponse(user);
    }

    private Map<String, Object> introspect(String ssoToken) {
        try {
            String url = UriComponentsBuilder.fromUriString(ssoIntrospectUrl)
                    .queryParam("token", ssoToken)
                    .build()
                    .toUriString();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);
            return response;
        } catch (Exception exception) {
            // Never include the token itself in logs or messages.
            log.warn("event=drive_sso_introspect_failed reason={}",
                    exception.getClass().getSimpleName());
            throw new AuthenticationFailedException(
                    "Sign-in service is unreachable. Please try again.");
        }
    }

    private DriveUser findOrProvision(String email, Map<String, Object> claims) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            String username = asString(claims.get("username"));
            if (username == null || username.isBlank()) {
                username = email.split("@")[0];
            }
            return userRepository.save(DriveUser.builder()
                    .email(email)
                    .ssoSubject(asString(claims.get("sub")))
                    .firstName(asString(claims.get("first_name")))
                    .lastName(asString(claims.get("last_name")))
                    .username(username)
                    .build());
        });
    }

    private AuthSessionResponse issueSession(DriveUser user) {
        String accessToken = jwtService.generateToken(String.valueOf(user.getId()), user.getEmail());
        String refreshToken = newRefreshToken();

        refreshTokenRepository.save(DriveRefreshToken.builder()
                .tokenHash(hash(refreshToken))
                .userId(user.getId())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshExpirationMs, ChronoUnit.MILLIS))
                .build());

        return new AuthSessionResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.expirationMs() / 1000,
                toUserResponse(user));
    }

    private AuthUserResponse toUserResponse(DriveUser user) {
        return new AuthUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
