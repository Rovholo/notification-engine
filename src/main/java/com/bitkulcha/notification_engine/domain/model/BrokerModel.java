package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
public interface BrokerModel {
    UUID getId();
    String getServer();
    String getUsername();
    String getPassword();
    boolean isSecure();
}
