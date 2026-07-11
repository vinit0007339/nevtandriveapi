package com.nevtan.drive.dto;

import java.time.Instant;

public record CreateShareLinkRequest(
        Instant expiresAt
) {
}
