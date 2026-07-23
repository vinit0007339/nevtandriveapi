package com.nevtan.drive.exception;

/** Raised when an SSO or Drive token is missing, invalid, or expired. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
