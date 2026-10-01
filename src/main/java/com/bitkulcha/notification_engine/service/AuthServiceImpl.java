package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.exception.EmailAlreadyExistsException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.exception.InvalidRefreshTokenException;
import com.bitkulcha.notification_engine.exception.TooManyLoginAttemptsException;
import com.bitkulcha.notification_engine.exception.UsernameAlreadyExistsException;
import com.bitkulcha.notification_engine.model.CurrentUserResponse;
import com.bitkulcha.notification_engine.model.LoginRequest;
import com.bitkulcha.notification_engine.model.RegisterRequest;
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
    public AuthTokens register(RegisterRequest request) {
        if (credentialRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UsernameAlreadyExistsException("Username is already taken: " + request.getUsername());
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email address is already in use");
        }

        UserEntity user = new UserEntity();
        user.setName(request.getName());
        user.setSurname(request.getSurname());
        user.setEmail(request.getEmail());
        user.setCell(request.getCell());
        userRepository.save(user);

        CredentialEntity credential = new CredentialEntity();
        credential.setUsername(request.getUsername());
        credential.setPassword(passwordEncoder.encode(request.getPassword()));
        credential.setUser(user);
        try {
            // Flush so a concurrent registration of the same username or email fails here rather than at commit.
            credentialRepository.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains(USERNAME_UNIQUE_CONSTRAINT)) {
                throw new UsernameAlreadyExistsException("Username is already taken: " + request.getUsername());
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
    public AuthTokens login(LoginRequest request) {
        CredentialEntity credential = credentialRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        Instant now = Instant.now();
        if (credential.getLockedUntil() != null && credential.getLockedUntil().isAfter(now)) {
            throw new TooManyLoginAttemptsException("Too many unsuccessful login attempts. Please try again later");
        }

        if (!passwordEncoder.matches(request.getPassword(), credential.getPassword())) {
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
    public AuthTokens refresh(String refreshToken) {
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
    public CurrentUserResponse getCurrentUser(UUID userId) {
        CredentialEntity credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
        UserEntity user = credential.getUser();

        CurrentUserResponse response = new CurrentUserResponse(
                user.getId().toString(), credential.getUsername(), user.getName(), user.getSurname(), user.getEmail());
        response.setCell(user.getCell());
        return response;
    }

    private AuthTokens issueTokens(CredentialEntity credential) {
        UserEntity user = credential.getUser();
        String accessToken = jwtService.generateToken(user.getId(), credential.getUsername());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthTokens(accessToken, refreshToken);
    }
}
