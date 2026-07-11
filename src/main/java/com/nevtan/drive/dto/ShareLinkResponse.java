package com.nevtan.drive.dto;

import java.time.Instant;

public record ShareLinkResponse(
        Long id,
        Long fileId,
        String shareUrl,
        Instant expiresAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
