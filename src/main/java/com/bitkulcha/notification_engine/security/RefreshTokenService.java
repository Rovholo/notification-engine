package com.bitkulcha.notification_engine.security;

import com.bitkulcha.notification_engine.repository.RefreshTokenRepository;
import com.bitkulcha.notification_engine.repository.entity.RefreshTokenEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final Duration CLEANUP_DELAY = Duration.ofDays(1);

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration expiration;
    private final Duration reuseGrace;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-days}") long expirationDays,
            @Value("${jwt.refresh-reuse-grace-seconds}") long reuseGraceSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expiration = Duration.ofDays(expirationDays);
        this.reuseGrace = Duration.ofSeconds(reuseGraceSeconds);
    }

    public String issue(UserEntity user) {
        return create(user).rawToken();
    }

    public String rotate(RefreshTokenEntity existing) {
        Issued issued = create(existing.getUser());
        existing.setRevokedAt(Instant.now());
        existing.setReplacedById(issued.token().getId());
        refreshTokenRepository.save(existing);
        return issued.rawToken();
    }

    public Optional<RefreshTokenEntity> find(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken));
    }

    // A client whose refresh response was lost retries with the token it still holds. Within the grace period that
    // retry is allowed to continue from the token it was rotated into, as long as nothing else has used or revoked it.
    public Optional<RefreshTokenEntity> findRetrySuccessor(RefreshTokenEntity revoked) {
        if (revoked.getReplacedById() == null || revoked.getRevokedAt() == null) {
            return Optional.empty();
        }
        if (revoked.getRevokedAt().plus(reuseGrace).isBefore(Instant.now())) {
            return Optional.empty();
        }
        return refreshTokenRepository.findLockedById(revoked.getReplacedById())
                .filter(successor -> successor.getRevokedAt() == null);
    }

    public void revoke(RefreshTokenEntity token) {
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        }
    }

    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    // Expired tokens are kept for a day so reuse of one just past its expiry is still recognised, then removed.
    @Scheduled(cron = "${jwt.refresh-cleanup-cron}")
    @Transactional
    public int deleteExpired() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(CLEANUP_DELAY));
        log.info("Deleted {} expired refresh tokens", deleted);
        return deleted;
    }

    private Issued create(UserEntity user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(expiration));
        refreshTokenRepository.save(token);

        return new Issued(token, rawToken);
    }

    private record Issued(RefreshTokenEntity token, String rawToken) {
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
