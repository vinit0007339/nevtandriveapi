package com.nevtan.drive.dto;

/** The Drive session handed back after a successful SSO exchange or refresh. */
public record AuthSessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        AuthUserResponse user
) {
}
