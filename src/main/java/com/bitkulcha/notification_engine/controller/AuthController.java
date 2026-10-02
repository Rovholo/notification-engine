package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.AuthApi;
import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModel;
import com.bitkulcha.notification_engine.domain.model.RegistrationModel;
import com.bitkulcha.notification_engine.domain.model.RegistrationModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.model.AuthResponseDto;
import com.bitkulcha.notification_engine.model.CurrentUserResponseDto;
import com.bitkulcha.notification_engine.model.LoginRequestDto;
import com.bitkulcha.notification_engine.model.PasswordResetConfirmRequestDto;
import com.bitkulcha.notification_engine.model.PasswordResetRequestDto;
import com.bitkulcha.notification_engine.model.RefreshRequestDto;
import com.bitkulcha.notification_engine.model.RegisterRequestDto;
import com.bitkulcha.notification_engine.service.AuthService;
import com.bitkulcha.notification_engine.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
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
    public ResponseEntity<AuthResponseDto> register(RegisterRequestDto registerRequest) {
        RegistrationModel registration = RegistrationModelImmtbl.builder()
                .username(registerRequest.getUsername())
                .password(registerRequest.getPassword())
                .name(registerRequest.getName())
                .surname(registerRequest.getSurname())
                .email(registerRequest.getEmail())
                .cell(Optional.ofNullable(registerRequest.getCell()))
                .build();
        return ResponseEntity.ok(toResponse(authService.register(registration)));
    }

    @Override
    public ResponseEntity<AuthResponseDto> login(LoginRequestDto loginRequest) {
        return ResponseEntity.ok(toResponse(authService.login(loginRequest.getUsername(), loginRequest.getPassword())));
    }

    @Override
    public ResponseEntity<AuthResponseDto> refresh(RefreshRequestDto refreshRequest) {
        return ResponseEntity.ok(toResponse(authService.refresh(refreshRequest.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequestDto refreshRequest) {
        authService.logout(refreshRequest.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CurrentUserResponseDto> getCurrentUser() {
        // JwtAuthenticationFilter sets the user id as the principal.
        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(toCurrentUserResponse(authService.getCurrentUser(userId)));
    }

    @Override
    public ResponseEntity<Void> requestPasswordReset(PasswordResetRequestDto passwordResetRequest) {
        passwordResetService.requestReset(passwordResetRequest.getEmail());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirmRequestDto request) {
        passwordResetService.confirmReset(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    private static AuthResponseDto toResponse(AuthTokensModel tokens) {
        return new AuthResponseDto(tokens.getAccessToken(), tokens.getRefreshToken());
    }

    private static CurrentUserResponseDto toCurrentUserResponse(AccountModel account) {
        UserModel user = account.getUser();
        CurrentUserResponseDto response = new CurrentUserResponseDto(
                user.getId().toString(), account.getUsername(), user.getName(), user.getSurname(), user.getEmail());
        response.setCell(user.getCell().orElse(null));
        return response;
    }
}
