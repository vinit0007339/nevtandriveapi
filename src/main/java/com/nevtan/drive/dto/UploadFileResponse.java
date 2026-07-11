package com.nevtan.drive.dto;

public record UploadFileResponse(
        String message,
        DriveFileResponse file
) {
}
