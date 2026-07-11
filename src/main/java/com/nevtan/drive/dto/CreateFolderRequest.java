package com.nevtan.drive.dto;

public record CreateFolderRequest(
        String name,
        Long parentFolderId
) {
}
