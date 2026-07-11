package com.nevtan.drive.exception;

public class FolderNotEmptyException extends RuntimeException {

    public FolderNotEmptyException(Long folderId) {
        super("Folder is not empty: " + folderId);
    }
}
