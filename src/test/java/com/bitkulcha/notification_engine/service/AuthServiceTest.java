package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.RegistrationModel;
import com.bitkulcha.notification_engine.domain.model.RegistrationModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.Role;
import com.bitkulcha.notification_engine.exception.EmailAlreadyExistsException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.exception.InvalidRefreshTokenException;
import com.bitkulcha.notification_engine.exception.TooManyLoginAttemptsException;
import com.bitkulcha.notification_engine.exception.UsernameAlreadyExistsException;
import com.bitkulcha.notification_engine.repository.CredentialRepository;
import com.bitkulcha.notification_engine.repository.UserFirebaseAuthRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.RefreshTokenEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.bitkulcha.notification_engine.repository.entity.UserFirebaseAuthEntity;
import com.bitkulcha.notification_engine.security.FirebasePasswordVerifier;
import com.bitkulcha.notification_engine.security.JwtService;
import com.bitkulcha.notification_engine.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserFirebaseAuthRepository userFirebaseAuthRepository;

    @Mock
    private FirebasePasswordVerifier firebasePasswordVerifier;

    @InjectMocks
    private AuthService authService;

    private static RegistrationModel registration() {
        return RegistrationModelImmtbl.builder()
                .username("alice")
                .password("s3cret")
                .name("Alice")
                .surname("Smith")
                .email("alice@example.com")
                .build();
    }

    private static AuthTokensModel tokens(String accessToken, String refreshToken) {
        return AuthTokensModelImmtbl.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }

    private static CredentialEntity credential(UserEntity user) {
        CredentialEntity credential = new CredentialEntity();
        credential.setUsername("alice");
        credential.setPassword("hashed-password");
        credential.setUser(user);
        return credential;
    }

    @Test
    void register_whenUsernameAndEmailAreFree_createsUserAndCredentialAndReturnsTokens() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret")).thenReturn("hashed-password");
        when(jwtService.generateToken(any(UUID.class), eq("alice"), any())).thenReturn("token-123");
        when(refreshTokenService.issue(any(UserEntity.class))).thenReturn("refresh-123");

        AuthTokensModel tokens = authService.register(registration());

        assertThat(tokens).isEqualTo(tokens("token-123", "refresh-123"));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getName()).isEqualTo("Alice");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("alice@example.com");

        ArgumentCaptor<CredentialEntity> credentialCaptor = ArgumentCaptor.forClass(CredentialEntity.class);
        verify(credentialRepository).saveAndFlush(credentialCaptor.capture());
        assertThat(credentialCaptor.getValue().getUsername()).isEqualTo("alice");
        assertThat(credentialCaptor.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(credentialCaptor.getValue().getUser()).isSameAs(userCaptor.getValue());
        verify(refreshTokenService).issue(userCaptor.getValue());
    }

    @Test
    void register_whenUsernameIsTaken_throwsAndDoesNotCreateAnything() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(new CredentialEntity()));

        assertThatThrownBy(() -> authService.register(registration()))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_whenEmailIsTaken_throwsAndDoesNotCreateAnything() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(new UserEntity()));

        assertThatThrownBy(() -> authService.register(registration()))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_whenUsernameIsTakenConcurrently_throwsUsernameAlreadyExists() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(credentialRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Duplicate entry 'alice' for key 'uk_credential_username'")));

        assertThatThrownBy(() -> authService.register(registration()))
                .isInstanceOf(UsernameAlreadyExistsException.class);

        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    void register_whenEmailIsTakenConcurrently_throwsEmailAlreadyExists() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(credentialRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Duplicate entry 'alice@example.com' for key 'uk_users_email'")));

        assertThatThrownBy(() -> authService.register(registration()))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void register_whenOtherConstraintIsViolated_rethrowsOriginalException() {
        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        DataIntegrityViolationException violation = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("Data too long for column 'username'"));
        when(credentialRepository.saveAndFlush(any())).thenThrow(violation);

        assertThatThrownBy(() -> authService.register(registration()))
                .isSameAs(violation);
    }

    @Test
    void login_withCorrectPassword_returnsTokensAndClearsFailedAttempts() {
        UserEntity user = new UserEntity();
        CredentialEntity credential = credential(user);
        credential.setFailedLoginAttempts(3);

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("s3cret", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), "alice", Set.of())).thenReturn("token-123");
        when(refreshTokenService.issue(user)).thenReturn("refresh-123");

        AuthTokensModel tokens = authService.login("alice", "s3cret");

        assertThat(tokens).isEqualTo(tokens("token-123", "refresh-123"));
        assertThat(credential.getFailedLoginAttempts()).isZero();
        verify(credentialRepository).save(credential);
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentialsAndCountsTheAttempt() {
        CredentialEntity credential = credential(new UserEntity());

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(credential.getLockedUntil()).isNull();
        verify(credentialRepository).save(credential);
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    void login_withFifthWrongPassword_locksTheAccount() {
        CredentialEntity credential = credential(new UserEntity());
        credential.setFailedLoginAttempts(4);

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.getLockedUntil()).isAfter(Instant.now().plus(Duration.ofMinutes(14)));
        assertThat(credential.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_whileLocked_throwsTooManyAttemptsWithoutCheckingPassword() {
        CredentialEntity credential = credential(new UserEntity());
        credential.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> authService.login("alice", "s3cret"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_afterLockExpires_allowsCorrectPassword() {
        UserEntity user = new UserEntity();
        CredentialEntity credential = credential(user);
        credential.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1)));

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("s3cret", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), "alice", Set.of())).thenReturn("token-123");
        when(refreshTokenService.issue(user)).thenReturn("refresh-123");

        authService.login("alice", "s3cret");

        assertThat(credential.getLockedUntil()).isNull();
    }

    @Test
    void login_putsTheUsersRolesInTheAccessToken() {
        UserEntity user = new UserEntity();
        user.getRoles().add(Role.ADMIN);
        CredentialEntity credential = credential(user);

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("s3cret", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user.getId(), "alice", Set.of(Role.ADMIN))).thenReturn("token-123");
        when(refreshTokenService.issue(user)).thenReturn("refresh-123");

        assertThat(authService.login("alice", "s3cret").getAccessToken()).isEqualTo("token-123");
    }

    @Test
    void login_whenUsernameDoesNotExist_throwsInvalidCredentials() {
        when(credentialRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    private static UserFirebaseAuthEntity firebaseAuth(UserEntity user) {
        UserFirebaseAuthEntity firebaseAuth = new UserFirebaseAuthEntity();
        firebaseAuth.setUser(user);
        firebaseAuth.setFirebaseUid("firebase-uid");
        firebaseAuth.setFirebaseEmail("alice@firebase.example.com");
        return firebaseAuth;
    }

    @Test
    void login_withoutLocalPassword_whenFirebaseAcceptsIt_storesThePasswordAndReturnsTokens() {
        UserEntity user = new UserEntity();
        CredentialEntity credential = credential(user);
        credential.setPassword("");

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(userFirebaseAuthRepository.findByUserId(user.getId())).thenReturn(Optional.of(firebaseAuth(user)));
        when(firebasePasswordVerifier.verify("alice@firebase.example.com", "s3cret", "firebase-uid")).thenReturn(true);
        when(passwordEncoder.encode("s3cret")).thenReturn("hashed-password");
        when(jwtService.generateToken(user.getId(), "alice", Set.of())).thenReturn("token-123");
        when(refreshTokenService.issue(user)).thenReturn("refresh-123");

        AuthTokensModel tokens = authService.login("alice", "s3cret");

        assertThat(tokens).isEqualTo(tokens("token-123", "refresh-123"));
        assertThat(credential.getPassword()).isEqualTo("hashed-password");
        verify(credentialRepository).save(credential);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_withoutLocalPassword_whenFirebaseEmailIsMissing_usesTheUserEmail() {
        UserEntity user = new UserEntity();
        user.setEmail("alice@example.com");
        CredentialEntity credential = credential(user);
        credential.setPassword("");
        UserFirebaseAuthEntity firebaseAuth = firebaseAuth(user);
        firebaseAuth.setFirebaseEmail(null);

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(userFirebaseAuthRepository.findByUserId(user.getId())).thenReturn(Optional.of(firebaseAuth));
        when(firebasePasswordVerifier.verify("alice@example.com", "s3cret", "firebase-uid")).thenReturn(true);
        when(passwordEncoder.encode("s3cret")).thenReturn("hashed-password");
        when(jwtService.generateToken(user.getId(), "alice", Set.of())).thenReturn("token-123");
        when(refreshTokenService.issue(user)).thenReturn("refresh-123");

        authService.login("alice", "s3cret");

        assertThat(credential.getPassword()).isEqualTo("hashed-password");
    }

    @Test
    void login_withoutLocalPassword_whenFirebaseRejectsIt_throwsInvalidCredentialsAndCountsTheAttempt() {
        UserEntity user = new UserEntity();
        CredentialEntity credential = credential(user);
        credential.setPassword("");

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(userFirebaseAuthRepository.findByUserId(user.getId())).thenReturn(Optional.of(firebaseAuth(user)));
        when(firebasePasswordVerifier.verify("alice@firebase.example.com", "wrong", "firebase-uid")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(credential.getPassword()).isEmpty();
        assertThat(credential.getFailedLoginAttempts()).isEqualTo(1);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void login_withoutLocalPassword_whenUserIsNotAFirebaseUser_throwsInvalidCredentials() {
        UserEntity user = new UserEntity();
        CredentialEntity credential = credential(user);
        credential.setPassword("");

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(userFirebaseAuthRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("alice", "s3cret"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(firebasePasswordVerifier);
    }

    @Test
    void login_withoutLocalPassword_withEmptyPassword_throwsWithoutCallingFirebase() {
        CredentialEntity credential = credential(new UserEntity());
        credential.setPassword("");

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> authService.login("alice", ""))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userFirebaseAuthRepository, firebasePasswordVerifier);
    }

    @Test
    void login_withLocalPassword_neverCallsFirebase() {
        CredentialEntity credential = credential(new UserEntity());

        when(credentialRepository.findByUsername("alice")).thenReturn(Optional.of(credential));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(userFirebaseAuthRepository, firebasePasswordVerifier);
    }

    @Test
    void refresh_withActiveToken_revokesItAndIssuesNewTokens() {
        UserEntity user = new UserEntity();
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));

        when(refreshTokenService.find("refresh-old")).thenReturn(Optional.of(existing));
        when(credentialRepository.findByUserId(user.getId())).thenReturn(Optional.of(credential(user)));
        when(jwtService.generateToken(user.getId(), "alice", Set.of())).thenReturn("token-new");
        when(refreshTokenService.issue(user)).thenReturn("refresh-new");

        AuthTokensModel tokens = authService.refresh("refresh-old");

        assertThat(tokens).isEqualTo(tokens("token-new", "refresh-new"));
        verify(refreshTokenService).revoke(existing);
    }

    @Test
    void refresh_withUnknownToken_throwsInvalidRefreshToken() {
        when(refreshTokenService.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void refresh_withExpiredToken_throwsInvalidRefreshToken() {
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(new UserEntity());
        existing.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(refreshTokenService.find("refresh-old")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.refresh("refresh-old"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void refresh_withAlreadyRevokedToken_revokesEverySessionForTheUser() {
        UserEntity user = new UserEntity();
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);
        existing.setExpiresAt(Instant.now().plus(Duration.ofDays(1)));
        existing.setRevokedAt(Instant.now().minus(Duration.ofMinutes(5)));
        when(refreshTokenService.find("refresh-old")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.refresh("refresh-old"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenService).revokeAll(user.getId());
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    void logout_revokesTheToken() {
        RefreshTokenEntity existing = new RefreshTokenEntity();
        when(refreshTokenService.find("refresh-123")).thenReturn(Optional.of(existing));

        authService.logout("refresh-123");

        verify(refreshTokenService).revoke(existing);
    }

    @Test
    void logout_withUnknownToken_doesNothing() {
        when(refreshTokenService.find("nope")).thenReturn(Optional.empty());

        authService.logout("nope");

        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    void getCurrentUser_returnsProfileAndUsername() {
        UserEntity user = new UserEntity();
        user.setName("Alice");
        user.setSurname("Smith");
        user.setEmail("alice@example.com");
        user.setCell("0821234567");
        when(credentialRepository.findByUserId(user.getId())).thenReturn(Optional.of(credential(user)));

        AccountModel account = authService.getCurrentUser(user.getId());

        assertThat(account.getUser().getId()).isEqualTo(user.getId());
        assertThat(account.getUsername()).isEqualTo("alice");
        assertThat(account.getUser().getEmail()).isEqualTo("alice@example.com");
        assertThat(account.getUser().getCell()).contains("0821234567");
    }
}
