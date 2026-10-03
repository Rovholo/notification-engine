package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.exception.InvalidResetCodeException;
import com.bitkulcha.notification_engine.repository.CredentialRepository;
import com.bitkulcha.notification_engine.repository.PasswordResetCodeRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.PasswordResetCodeEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.bitkulcha.notification_engine.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordResetCodeRepository passwordResetCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private static UserEntity user() {
        UserEntity user = new UserEntity();
        user.setEmail("alice@example.com");
        return user;
    }

    private static PasswordResetCodeEntity activeCode(UserEntity user) {
        PasswordResetCodeEntity code = new PasswordResetCodeEntity();
        code.setUser(user);
        code.setCodeHash("hashed-code");
        code.setCreatedAt(Instant.now().minus(Duration.ofMinutes(5)));
        code.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        return code;
    }

    @Test
    void requestReset_forKnownEmail_storesHashedCodeAndEmailsIt() {
        UserEntity user = user();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-code");

        passwordResetService.requestReset("alice@example.com");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(eq("alice@example.com"), codeCaptor.capture(), any(Duration.class));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        verify(passwordEncoder).encode(codeCaptor.getValue());

        ArgumentCaptor<PasswordResetCodeEntity> savedCaptor = ArgumentCaptor.forClass(PasswordResetCodeEntity.class);
        verify(passwordResetCodeRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getCodeHash()).isEqualTo("hashed-code");
        assertThat(savedCaptor.getValue().getUser()).isSameAs(user);
        verify(passwordResetCodeRepository).invalidateAllForUser(eq(user.getId()), any(Instant.class));
    }

    @Test
    void requestReset_forUnknownEmail_doesNothing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestReset("ghost@example.com");

        verifyNoInteractions(emailService, passwordResetCodeRepository);
    }

    @Test
    void requestReset_withinCooldown_doesNotSendAnotherCode() {
        UserEntity user = user();
        PasswordResetCodeEntity recent = activeCode(user);
        recent.setCreatedAt(Instant.now().minus(Duration.ofSeconds(10)));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(recent));

        passwordResetService.requestReset("alice@example.com");

        verifyNoInteractions(emailService);
        verify(passwordResetCodeRepository, never()).save(any());
    }

    @Test
    void confirmReset_withCorrectCode_changesPasswordAndEndsAllSessions() {
        UserEntity user = user();
        PasswordResetCodeEntity code = activeCode(user);
        CredentialEntity credential = new CredentialEntity();
        credential.setPassword("old-hash");
        credential.setFailedLoginAttempts(3);
        credential.setLockedUntil(Instant.now().plus(Duration.ofMinutes(5)));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);
        when(credentialRepository.findByUserId(user.getId())).thenReturn(Optional.of(credential));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        passwordResetService.confirmReset("alice@example.com", "123456", "new-password");

        assertThat(code.getUsedAt()).isNotNull();
        assertThat(credential.getPassword()).isEqualTo("new-hash");
        assertThat(credential.getFailedLoginAttempts()).isZero();
        assertThat(credential.getLockedUntil()).isNull();
        verify(credentialRepository).save(credential);
        verify(refreshTokenService).revokeAll(user.getId());
    }

    @Test
    void confirmReset_withWrongCode_countsTheAttemptAndKeepsPassword() {
        UserEntity user = user();
        PasswordResetCodeEntity code = activeCode(user);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(code));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThatThrownBy(() -> passwordResetService.confirmReset("alice@example.com", "000000", "new-password"))
                .isInstanceOf(InvalidResetCodeException.class);

        assertThat(code.getAttempts()).isEqualTo(1);
        verify(passwordResetCodeRepository).save(code);
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void confirmReset_afterTooManyAttempts_rejectsEvenTheCorrectCode() {
        UserEntity user = user();
        PasswordResetCodeEntity code = activeCode(user);
        code.setAttempts(5);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> passwordResetService.confirmReset("alice@example.com", "123456", "new-password"))
                .isInstanceOf(InvalidResetCodeException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void confirmReset_withExpiredCode_throwsInvalidResetCode() {
        UserEntity user = user();
        PasswordResetCodeEntity code = activeCode(user);
        code.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> passwordResetService.confirmReset("alice@example.com", "123456", "new-password"))
                .isInstanceOf(InvalidResetCodeException.class);
    }

    @Test
    void confirmReset_forUnknownEmail_throwsInvalidResetCode() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.confirmReset("ghost@example.com", "123456", "new-password"))
                .isInstanceOf(InvalidResetCodeException.class);
    }
}
