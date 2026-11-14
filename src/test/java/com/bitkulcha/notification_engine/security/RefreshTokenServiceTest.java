package com.bitkulcha.notification_engine.security;

import com.bitkulcha.notification_engine.repository.RefreshTokenRepository;
import com.bitkulcha.notification_engine.repository.entity.RefreshTokenEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 30, 60);
    }

    @Test
    void issue_storesOnlyTheHashAndReturnsTheRawToken() {
        UserEntity user = new UserEntity();

        String rawToken = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenEntity saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenService.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(29)));
    }

    @Test
    void issue_returnsDifferentTokensEachTime() {
        UserEntity user = new UserEntity();

        assertThat(refreshTokenService.issue(user)).isNotEqualTo(refreshTokenService.issue(user));
    }

    @Test
    void find_looksUpByHash() {
        RefreshTokenEntity token = new RefreshTokenEntity();
        when(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(refreshTokenService.find("raw")).contains(token);
    }

    @Test
    void revoke_onAlreadyRevokedToken_doesNotSave() {
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setRevokedAt(Instant.now());

        refreshTokenService.revoke(token);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotate_revokesTheOldTokenAndLinksItToTheNewOne() {
        UserEntity user = new UserEntity();
        RefreshTokenEntity existing = new RefreshTokenEntity();
        existing.setUser(user);

        String rawToken = refreshTokenService.rotate(existing);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        List<RefreshTokenEntity> saved = captor.getAllValues();
        RefreshTokenEntity created = saved.getFirst();
        assertThat(created.getTokenHash()).isEqualTo(RefreshTokenService.hash(rawToken));
        assertThat(created.getUser()).isSameAs(user);
        assertThat(saved.get(1)).isSameAs(existing);
        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(existing.getReplacedById()).isEqualTo(created.getId());
    }

    @Test
    void findRetrySuccessor_withinGracePeriod_returnsTheActiveSuccessor() {
        RefreshTokenEntity successor = new RefreshTokenEntity();
        RefreshTokenEntity revoked = rotatedInto(successor.getId(), Duration.ofSeconds(10));
        when(refreshTokenRepository.findLockedById(successor.getId())).thenReturn(Optional.of(successor));

        assertThat(refreshTokenService.findRetrySuccessor(revoked)).contains(successor);
    }

    @Test
    void findRetrySuccessor_afterGracePeriod_returnsEmpty() {
        RefreshTokenEntity revoked = rotatedInto(UUID.randomUUID(), Duration.ofSeconds(61));

        assertThat(refreshTokenService.findRetrySuccessor(revoked)).isEmpty();
        verify(refreshTokenRepository, never()).findLockedById(any());
    }

    @Test
    void findRetrySuccessor_whenSuccessorIsRevoked_returnsEmpty() {
        RefreshTokenEntity successor = new RefreshTokenEntity();
        successor.setRevokedAt(Instant.now());
        RefreshTokenEntity revoked = rotatedInto(successor.getId(), Duration.ofSeconds(10));
        when(refreshTokenRepository.findLockedById(successor.getId())).thenReturn(Optional.of(successor));

        assertThat(refreshTokenService.findRetrySuccessor(revoked)).isEmpty();
    }

    @Test
    void findRetrySuccessor_forLoggedOutToken_returnsEmpty() {
        RefreshTokenEntity revoked = new RefreshTokenEntity();
        revoked.setRevokedAt(Instant.now());

        assertThat(refreshTokenService.findRetrySuccessor(revoked)).isEmpty();
        verify(refreshTokenRepository, never()).findLockedById(any());
    }

    @Test
    void deleteExpired_keepsTokensForADayAfterTheyExpire() {
        when(refreshTokenRepository.deleteExpiredBefore(any())).thenReturn(3);

        assertThat(refreshTokenService.deleteExpired()).isEqualTo(3);

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteExpiredBefore(captor.capture());
        assertThat(captor.getValue())
                .isBefore(Instant.now().minus(Duration.ofHours(23)))
                .isAfter(Instant.now().minus(Duration.ofHours(25)));
    }

    private static RefreshTokenEntity rotatedInto(UUID successorId, Duration ago) {
        RefreshTokenEntity revoked = new RefreshTokenEntity();
        revoked.setRevokedAt(Instant.now().minus(ago));
        revoked.setReplacedById(successorId);
        return revoked;
    }
}
