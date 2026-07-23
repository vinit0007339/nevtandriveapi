package com.nevtan.drive.dto;

/** The signed-in Drive user's profile, sourced from NevTan SSO. */
public record AuthUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String username
) {
}
