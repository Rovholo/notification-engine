package com.bitkulcha.notification_engine.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "unit-test-secret-key-0123456789-abcdefghijklmnop", 60L);

    @Test
    void generateToken_thenValidate_returnsOriginalUserId() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId, "alice", Set.of());

        assertThat(jwtService.validate(token)).contains(new AccessToken(userId, Set.of()));
    }

    @Test
    void validate_whenTokenIsGarbage_returnsEmpty() {
        Optional<AccessToken> result = jwtService.validate("not-a-real-token");

        assertThat(result).isEmpty();
    }

    @Test
    void validate_whenSignedWithDifferentKey_returnsEmpty() {
        JwtService otherService = new JwtService(
                "a-completely-different-secret-key-0123456789", 60L);
        String token = otherService.generateToken(UUID.randomUUID(), "alice", Set.of());

        assertThat(jwtService.validate(token)).isEmpty();
    }

    @Test
    void validate_whenTokenExpired_returnsEmpty() throws InterruptedException {
        JwtService shortLivedService = new JwtService(
                "unit-test-secret-key-0123456789-abcdefghijklmnop", 0L);
        String token = shortLivedService.generateToken(UUID.randomUUID(), "alice", Set.of());

        Thread.sleep(50);

        assertThat(jwtService.validate(token)).isEmpty();
    }
}
