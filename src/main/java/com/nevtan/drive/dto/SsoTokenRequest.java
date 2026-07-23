package com.nevtan.drive.dto;

import jakarta.validation.constraints.NotBlank;

/** Carries the NevTan SSO access token to be exchanged for a Drive session. */
public class SsoTokenRequest {

    @NotBlank
    private String ssoToken;

    public String getSsoToken() {
        return ssoToken;
    }

    public void setSsoToken(String ssoToken) {
        this.ssoToken = ssoToken;
    }
}
