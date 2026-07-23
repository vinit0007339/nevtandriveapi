package com.nevtan.drive.controller;

import com.nevtan.drive.auth.CurrentUserService;
import com.nevtan.drive.dto.AuthSessionResponse;
import com.nevtan.drive.dto.AuthUserResponse;
import com.nevtan.drive.dto.RefreshTokenRequest;
import com.nevtan.drive.dto.SsoTokenRequest;
import com.nevtan.drive.service.DriveAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session endpoints. Credentials are never posted here: the client signs in
 * against NevTan SSO and exchanges the resulting token for a Drive session.
 */
@RestController
@RequestMapping("/api/drive/auth")
@RequiredArgsConstructor
public class DriveAuthController {

    private final DriveAuthService authService;
    private final CurrentUserService currentUserService;

    @PostMapping("/sso")
    public ResponseEntity<AuthSessionResponse> ssoLogin(
            @Valid @RequestBody SsoTokenRequest request
    ) {
        return ResponseEntity.ok(authService.ssoLogin(request.getSsoToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(
            @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        authService.logout(request == null ? null : request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {
        return ResponseEntity.ok(
                authService.describe(currentUserService.currentUser().email()));
    }
}
