package com.nevtan.drive.dto;

import java.time.Instant;
import java.util.List;

public record DriveFileDetailsResponse(
        Long id,
        String fileName,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String type,
        String owner,
        String location,
        Long folderId,
        List<DriveFolderResponse> folderPath,
        boolean shared,
        boolean starred,
        Instant createdAt,
        Instant updatedAt,
        Instant lastOpenedAt
) {
}
