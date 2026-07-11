package com.nevtan.drive.exception;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(Long folderId) {
        super("Folder not found: " + folderId);
    }
}
