package com.nevtan.drive.dto;

/** Carries the Drive refresh token. Also used by logout, where it is optional. */
public class RefreshTokenRequest {

    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
