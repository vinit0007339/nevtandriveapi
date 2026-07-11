package com.nevtan.drive.dto;

public record FilePreviewMetadataResponse(
        Long id,
        String fileName,
        String originalFileName,
        String contentType,
        long sizeBytes,
        boolean previewSupported,
        String downloadUrl
) {
}
