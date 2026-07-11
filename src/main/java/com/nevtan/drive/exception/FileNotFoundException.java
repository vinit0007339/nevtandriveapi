package com.nevtan.drive.exception;

public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(Long fileId) {
        super("File not found: " + fileId);
    }
}
