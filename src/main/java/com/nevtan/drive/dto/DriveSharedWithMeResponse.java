package com.nevtan.drive.dto;

import java.util.List;

public record DriveSharedWithMeResponse(
        List<SharedDriveFileResponse> files,
        List<SharedDriveFolderResponse> folders
) {
}
