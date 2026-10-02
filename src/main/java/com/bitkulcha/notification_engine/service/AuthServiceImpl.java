package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.RegistrationModel;
import com.bitkulcha.notification_engine.exception.EmailAlreadyExistsException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.exception.InvalidRefreshTokenException;
import com.bitkulcha.notification_engine.exception.TooManyLoginAttemptsException;
import com.bitkulcha.notification_engine.exception.UsernameAlreadyExistsException;
import com.bitkulcha.notification_engine.repository.CredentialRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.RefreshTokenEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.bitkulcha.notification_engine.security.JwtService;
import com.bitkulcha.notification_engine.security.RefreshTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String USERNAME_UNIQUE_CONSTRAINT = "uk_credential_username";
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_users_email";
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    @Transactional
    public AuthTokensModel register(RegistrationModel registration) {
        if (credentialRepository.findByUsername(registration.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username is already taken: " + registration.getUsername());
        }
        if (userRepository.findByEmail(registration.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email address is already in use");
        }

        UserEntity user = new UserEntity();
        user.setName(registration.getName());
        user.setSurname(registration.getSurname());
        user.setEmail(registration.getEmail());
        user.setCell(registration.getCell().orElse(null));
        userRepository.save(user);

        CredentialEntity credential = new CredentialEntity();
        credential.setUsername(registration.getUsername());
        credential.setPassword(passwordEncoder.encode(registration.getPassword()));
        credential.setUser(user);
        try {
            // Flush so a concurrent registration of the same username or email fails here rather than at commit.
            credentialRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(USERNAME_UNIQUE_CONSTRAINT)) {
                throw new UsernameAlreadyExistsException("Username is already taken: " + registration.getUsername());
            }
            if (cause != null && cause.contains(EMAIL_UNIQUE_CONSTRAINT)) {
                throw new EmailAlreadyExistsException("Email address is already in use");
            }
            throw e;
        }

        return issueTokens(credential);
    }

    // Failed-attempt counts must be committed even though the login fails.
    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokensModel login(String username, String password) {
        CredentialEntity credential = credentialRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        Instant now = Instant.now();
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            throw new TooManyLoginAttemptsException("Too many unsuccessful login attempts. Please try again later");
        }

        if (!passwordEncoder.matches(password, credential.getPassword())) {
            int attempts = credential.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                credential.setFailedLoginAttempts(0);
                credential.setLockedUntil(now.plus(LOGIN_LOCKOUT));
            } else {
                credential.setFailedLoginAttempts(attempts);
            }
            credentialRepository.save(credential);
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (credential.getFailedLoginAttempts() > 0 || credential.getLockedUntil() != null) {
            credential.setFailedLoginAttempts(0);
            credential.setLockedUntil(null);
            credentialRepository.save(credential);
        }

        return issueTokens(credential);
    }

    // Revoking every session on token reuse must be committed even though the refresh fails.
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthTokensModel refresh(String refreshToken) {
        RefreshTokenEntity existing = refreshTokenService.find(refreshToken)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        UUID userId = existing.getUser().getId();

        if (existing.getRevokedAt() != null) {
            // Refresh tokens are single use, so seeing a revoked one again means it may have been stolen.
            refreshTokenService.revokeAll(userId);
            throw new InvalidRefreshTokenException("Invalid refresh token");
        }
        if (!existing.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        refreshTokenService.revoke(existing);
        CredentialEntity credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        return issueTokens(credential);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.find(refreshToken).ifPresent(refreshTokenService::revoke);
    }

    @Override
    public AccountModel getCurrentUser(UUID userId) {
        return credentialRepository.findByUserId(userId)
                .map(EntityModelMapper::toModel)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
    }

    private AuthTokensModel issueTokens(CredentialEntity credential) {
        UserEntity user = credential.getUser();
        String accessToken = jwtService.generateToken(user.getId(), credential.getUsername());
        String refreshToken = refreshTokenService.issue(user);
        return AuthTokensModelImmtbl.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
