package com.nevtan.drive.dto;

import java.time.Instant;

public record SharedDriveFolderResponse(
        Long id,
        String name,
        Long parentFolderId,
        Instant createdAt,
        Instant updatedAt,
        Long permissionId,
        String ownerEmail,
        String sharedWithEmail,
        String role
) {
}
