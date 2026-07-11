package com.nevtan.drive.dto;

import java.time.Instant;

public record UpdateShareLinkRequest(
        Instant expiresAt,
        Boolean active
) {
}
