package com.nevtan.drive.dto;

import java.time.Instant;

public record SharedDriveFileResponse(
        Long id,
        String fileName,
        String originalFileName,
        String contentType,
        long sizeBytes,
        Long folderId,
        boolean shared,
        boolean starred,
        Instant createdAt,
        Instant updatedAt,
        Instant lastOpenedAt,
        Long permissionId,
        String ownerEmail,
        String sharedWithEmail,
        String role
) {
}
