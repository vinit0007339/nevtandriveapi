package com.nevtan.drive.dto;

import java.time.Instant;

public record DriveFileResponse(
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
        Instant lastOpenedAt
) {
}
