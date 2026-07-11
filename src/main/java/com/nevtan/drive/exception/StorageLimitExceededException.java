package com.nevtan.drive.exception;

public class StorageLimitExceededException extends RuntimeException {

    public StorageLimitExceededException(long usedBytes, long uploadBytes, long limitBytes) {
        super("Storage limit exceeded: used=" + usedBytes
                + " bytes, upload=" + uploadBytes
                + " bytes, limit=" + limitBytes + " bytes");
    }
}
