package com.nevtan.drive.dto;

public record CreateDrivePermissionRequest(
        String sharedWithEmail,
        String role
) {
}
