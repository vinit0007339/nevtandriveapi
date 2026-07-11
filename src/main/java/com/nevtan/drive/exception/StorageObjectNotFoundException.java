package com.nevtan.drive.exception;

public class StorageObjectNotFoundException extends RuntimeException {

    public StorageObjectNotFoundException() {
        super("Stored file content was not found");
    }
}
