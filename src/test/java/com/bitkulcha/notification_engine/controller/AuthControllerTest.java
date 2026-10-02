package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AccountModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.RegistrationModel;
import com.bitkulcha.notification_engine.domain.model.RegistrationModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.UserModelImmtbl;
import com.bitkulcha.notification_engine.model.AuthResponseDto;
import com.bitkulcha.notification_engine.model.CurrentUserResponseDto;
import com.bitkulcha.notification_engine.model.LoginRequestDto;
import com.bitkulcha.notification_engine.model.PasswordResetConfirmRequestDto;
import com.bitkulcha.notification_engine.model.PasswordResetRequestDto;
import com.bitkulcha.notification_engine.model.RefreshRequestDto;
import com.bitkulcha.notification_engine.model.RegisterRequestDto;
import com.bitkulcha.notification_engine.service.AuthService;
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
        RegisterRequestDto request = new RegisterRequestDto("alice", "s3cret", "Alice", "Smith", "alice@example.com");
        request.setCell("0821234567");
        RegistrationModel registration = RegistrationModelImmtbl.builder()
                .username("alice")
                .password("s3cret")
                .name("Alice")
                .surname("Smith")
                .email("alice@example.com")
                .cell("0821234567")
                .build();
        when(authService.register(registration)).thenReturn(tokens("token-123", "refresh-123"));

        ResponseEntity<AuthResponseDto> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("token-123");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-123");
    }

    @Test
    void login_returnsTokensFromService() {
        LoginRequestDto request = new LoginRequestDto("alice", "s3cret");
        when(authService.login("alice", "s3cret")).thenReturn(tokens("token-123", "refresh-123"));

        ResponseEntity<AuthResponseDto> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("token-123");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-123");
    }

    @Test
    void refresh_returnsNewTokensFromService() {
        when(authService.refresh("refresh-old")).thenReturn(tokens("token-new", "refresh-new"));

        ResponseEntity<AuthResponseDto> response = authController.refresh(new RefreshRequestDto("refresh-old"));

        assertThat(response.getBody().getToken()).isEqualTo("token-new");
        assertThat(response.getBody().getRefreshToken()).isEqualTo("refresh-new");
    }

    @Test
    void logout_revokesTokenAndReturnsNoContent() {
        ResponseEntity<Void> response = authController.logout(new RefreshRequestDto("refresh-123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(authService).logout("refresh-123");
    }

    @Test
    void getCurrentUser_usesUserIdFromSecurityContext() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        AccountModel account = AccountModelImmtbl.builder()
                .username("alice")
                .user(UserModelImmtbl.builder()
                        .id(userId)
                        .name("Alice")
                        .surname("Smith")
                        .email("alice@example.com")
                        .cell("0821234567")
                        .build())
                .build();
        when(authService.getCurrentUser(userId)).thenReturn(account);

        ResponseEntity<CurrentUserResponseDto> response = authController.getCurrentUser();

        assertThat(response.getBody().getId()).isEqualTo(userId.toString());
        assertThat(response.getBody().getUsername()).isEqualTo("alice");
        assertThat(response.getBody().getName()).isEqualTo("Alice");
        assertThat(response.getBody().getSurname()).isEqualTo("Smith");
        assertThat(response.getBody().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getBody().getCell()).isEqualTo("0821234567");
    }

    @Test
    void requestPasswordReset_returnsNoContent() {
        ResponseEntity<Void> response = authController.requestPasswordReset(new PasswordResetRequestDto("alice@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).requestReset("alice@example.com");
    }

    @Test
    void confirmPasswordReset_returnsNoContent() {
        ResponseEntity<Void> response = authController.confirmPasswordReset(
                new PasswordResetConfirmRequestDto("alice@example.com", "123456", "new-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(passwordResetService).confirmReset("alice@example.com", "123456", "new-password");
    }

    private static AuthTokensModel tokens(String accessToken, String refreshToken) {
        return AuthTokensModelImmtbl.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }
}
