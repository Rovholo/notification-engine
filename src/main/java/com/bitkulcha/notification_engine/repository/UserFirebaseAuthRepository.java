package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.UserFirebaseAuthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFirebaseAuthRepository extends JpaRepository<UserFirebaseAuthEntity, UUID> {

    Optional<UserFirebaseAuthEntity> findByUserId(UUID userId);
}
