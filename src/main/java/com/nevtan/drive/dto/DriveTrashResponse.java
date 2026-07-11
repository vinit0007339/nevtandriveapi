package com.nevtan.drive.dto;

import java.util.List;

public record DriveTrashResponse(
        List<DriveFolderResponse> folders,
        List<DriveFileResponse> files
) {
}
