package com.nevtan.drive.dto;

import java.time.Instant;

public record DrivePermissionResponse(
        Long id,
        Long fileId,
        String ownerEmail,
        String sharedWithEmail,
        String role,
        Instant createdAt,
        Instant updatedAt
) {
}
