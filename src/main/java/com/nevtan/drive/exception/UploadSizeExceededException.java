package com.nevtan.drive.exception;

public class UploadSizeExceededException extends RuntimeException {

    public UploadSizeExceededException(long uploadBytes, long maximumBytes) {
        super("Upload size " + uploadBytes
                + " bytes exceeds maximum of " + maximumBytes + " bytes");
    }
}
