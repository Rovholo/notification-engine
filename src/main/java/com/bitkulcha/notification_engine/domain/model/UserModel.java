package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

import java.util.Optional;
import java.util.UUID;

@Value.Immutable
public interface UserModel {
    UUID getId();
    String getName();
    String getSurname();
    String getEmail();
    Optional<String> getCell();
}
