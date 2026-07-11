package com.nevtan.drive.dto;

import java.time.Instant;

public record DriveFolderResponse(
        Long id,
        String name,
        Long parentFolderId,
        Instant createdAt,
        Instant updatedAt
) {
}
