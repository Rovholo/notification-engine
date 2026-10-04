package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.bitkulcha.notification_engine.security.SecurityAuditorAware;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:jpa-auditing-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Import(JpaAuditingIntegrationTest.AuditingTestConfig.class)
class JpaAuditingIntegrationTest {

    @TestConfiguration
    @EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
    static class AuditingTestConfig {
        @Bean
        AuditorAware<UUID> securityAuditorAware() {
            return new SecurityAuditorAware();
        }
    }

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void savingEntity_withoutAnAuthenticatedUser_leavesUpdatedByNull() {
        UserEntity user = newUser("Alice", "alice@example.com");

        UserEntity saved = userRepository.saveAndFlush(user);

        assertThat(saved.getUpdatedBy()).isNull();
    }

    @Test
    void updatingEntity_asAnAuthenticatedUser_stampsUpdatedByWithThatUsersId() {
        UserEntity saved = userRepository.saveAndFlush(newUser("Bob", "bob@example.com"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(saved.getId(), null, List.of()));

        saved.setSurname("Jones-Smith");
        UserEntity updated = userRepository.saveAndFlush(saved);

        assertThat(updated.getUpdatedBy()).isEqualTo(saved.getId());
    }

    private UserEntity newUser(String name, String email) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(email);
        return user;
    }
}
