package com.nevtan.drive.exception;

public class DriveAccessDeniedException extends RuntimeException {

    public DriveAccessDeniedException() {
        super("You do not have permission to access this drive item");
    }
}
