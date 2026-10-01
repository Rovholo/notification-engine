package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.AuthApi;
import com.bitkulcha.notification_engine.model.AuthResponse;
import com.bitkulcha.notification_engine.model.CurrentUserResponse;
import com.bitkulcha.notification_engine.model.LoginRequest;
import com.bitkulcha.notification_engine.model.PasswordResetConfirmRequest;
import com.bitkulcha.notification_engine.model.PasswordResetRequest;
import com.bitkulcha.notification_engine.model.RefreshRequest;
import com.bitkulcha.notification_engine.model.RegisterRequest;
import com.bitkulcha.notification_engine.service.AuthService;
import com.bitkulcha.notification_engine.service.AuthTokens;
import com.bitkulcha.notification_engine.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Override
    public ResponseEntity<AuthResponse> register(RegisterRequest registerRequest) {
        return ResponseEntity.ok(toResponse(authService.register(registerRequest)));
    }

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest loginRequest) {
        return ResponseEntity.ok(toResponse(authService.login(loginRequest)));
    }

    @Override
    public ResponseEntity<AuthResponse> refresh(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(toResponse(authService.refresh(refreshRequest.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest refreshRequest) {
        authService.logout(refreshRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        // JwtAuthenticationFilter sets the user id as the principal.
        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(PasswordResetRequest passwordResetRequest) {
        passwordResetService.requestReset(passwordResetRequest.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private static AuthResponse toResponse(AuthTokens tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
