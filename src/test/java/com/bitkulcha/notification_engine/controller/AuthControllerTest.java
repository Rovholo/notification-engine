package com.bitkulcha.notification_engine.controller;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private AuthController authController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_returnsTokensFromService() {
        RegisterRequest request = new RegisterRequest("alice", "s3cret", "Alice", "Smith", "alice@example.com");
        when(authService.register(request)).thenReturn(new AuthTokens("token-123", "refresh-123"));

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("token-123");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-123");
    }

    @Test
    void login_returnsTokensFromService() {
        LoginRequest request = new LoginRequest("alice", "s3cret");
        when(authService.login(request)).thenReturn(new AuthTokens("token-123", "refresh-123"));

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("token-123");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-123");
    }

    @Test
    void refresh_returnsNewTokensFromService() {
        when(authService.refresh("refresh-old")).thenReturn(new AuthTokens("token-new", "refresh-new"));

        ResponseEntity<AuthResponse> response = authController.refresh(new RefreshRequest("refresh-old"));

        assertThat(response.getBody().getToken()).isEqualTo("token-new");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-new");
    }

    @Test
    void logout_revokesTokenAndReturnsNoContent() {
        ResponseEntity<Void> response = authController.logout(new RefreshRequest("refresh-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("refresh-123");
    }

    @Test
    void getCurrentUser_usesUserIdFromSecurityContext() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        CurrentUserResponse user = new CurrentUserResponse(userId.toString(), "alice", "Alice", "Smith", "alice@example.com");
        when(authService.getCurrentUser(userId)).thenReturn(user);

        ResponseEntity<CurrentUserResponse> response = authController.getCurrentUser();

        assertThat(response.getBody()).isSameAs(user);
    }

    @Test
    void requestPasswordReset_returnsNoContent() {
        ResponseEntity<Void> response = authController.requestPasswordReset(new PasswordResetRequest("alice@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).requestReset("alice@example.com");
    }

    @Test
    void confirmPasswordReset_returnsNoContent() {
        ResponseEntity<Void> response = authController.confirmPasswordReset(
                new PasswordResetConfirmRequest("alice@example.com", "123456", "new-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).confirmReset("alice@example.com", "123456", "new-password");
    }
}
