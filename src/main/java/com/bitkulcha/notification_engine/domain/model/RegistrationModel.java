package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
public interface RegistrationModel {
    String getUsername();
    String getPassword();
    String getName();
    String getSurname();
    String getEmail();
    Optional<String> getCell();
}
