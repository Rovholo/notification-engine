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
import java.util.Optional;

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
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 30);
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
}
