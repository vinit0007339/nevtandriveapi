package com.nevtan.drive.auth;

public record AuthenticatedUser(
        String id,
        String email
) {
}
