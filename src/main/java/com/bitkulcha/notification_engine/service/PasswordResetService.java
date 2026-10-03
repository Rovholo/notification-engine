package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.exception.InvalidResetCodeException;
import com.bitkulcha.notification_engine.repository.CredentialRepository;
import com.bitkulcha.notification_engine.repository.PasswordResetCodeRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.PasswordResetCodeEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.bitkulcha.notification_engine.security.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int CODE_DIGITS = 6;
    private static final Duration CODE_VALIDITY = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;

    public PasswordResetService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            PasswordResetCodeRepository passwordResetCodeRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.passwordResetCodeRepository = passwordResetCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
    }

    @Transactional
    public void requestReset(String email) {
        // Unknown emails return silently so callers can't discover which addresses are registered.
        Optional<UserEntity> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        Optional<PasswordResetCodeEntity> latest =
                passwordResetCodeRepository.findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.get().getId());
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN))) {
            return;
        }

        passwordResetCodeRepository.invalidateAllForUser(user.get().getId(), now);

        String code = generateCode();
        PasswordResetCodeEntity resetCode = new PasswordResetCodeEntity();
        resetCode.setUser(user.get());
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setCreatedAt(now);
        resetCode.setExpiresAt(now.plus(CODE_VALIDITY));
        passwordResetCodeRepository.save(resetCode);

        emailService.sendPasswordResetCode(user.get().getEmail(), code, CODE_VALIDITY);
    }

    // Failed attempts must be committed even though the reset fails.
    @Transactional(noRollbackFor = InvalidResetCodeException.class)
    public void confirmReset(String email, String code, String newPassword) {
        UserEntity user = userRepository.findByEmail(email).orElseThrow(PasswordResetService::invalidCode);
        PasswordResetCodeEntity resetCode = passwordResetCodeRepository
                .findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(PasswordResetService::invalidCode);

        Instant now = Instant.now();
        if (!resetCode.getExpiresAt().isAfter(now) || resetCode.getAttempts() >= MAX_CODE_ATTEMPTS) {
            throw invalidCode();
        }
        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            resetCode.setAttempts(resetCode.getAttempts() + 1);
            passwordResetCodeRepository.save(resetCode);
            throw invalidCode();
        }

        resetCode.setUsedAt(now);
        passwordResetCodeRepository.save(resetCode);

        CredentialEntity credential = credentialRepository.findByUserId(user.getId())
                .orElseThrow(PasswordResetService::invalidCode);
        credential.setPassword(passwordEncoder.encode(newPassword));
        credential.setFailedLoginAttempts(0);
        credential.setLockedUntil(null);
        credentialRepository.save(credential);

        // Log out every device that was using the old password.
        refreshTokenService.revokeAll(user.getId());
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", secureRandom.nextInt(bound));
    }

    private static InvalidResetCodeException invalidCode() {
        return new InvalidResetCodeException("Invalid or expired reset code");
    }
}
