package com.nevtan.drive.exception;

public class ShareLinkNotFoundException extends RuntimeException {

    public ShareLinkNotFoundException() {
        super("Share link is unavailable");
    }
}
