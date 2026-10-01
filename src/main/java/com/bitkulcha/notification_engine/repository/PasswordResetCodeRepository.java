package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.PasswordResetCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCodeEntity, UUID> {

    Optional<PasswordResetCodeEntity> findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query("UPDATE PasswordResetCodeEntity c SET c.usedAt = :now WHERE c.user.id = :userId AND c.usedAt IS NULL")
    int invalidateAllForUser(UUID userId, Instant now);
}
