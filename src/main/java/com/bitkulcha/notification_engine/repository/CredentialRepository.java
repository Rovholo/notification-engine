package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialRepository extends JpaRepository<CredentialEntity, UUID> {

    Optional<CredentialEntity> findByUsername(String username);
}
