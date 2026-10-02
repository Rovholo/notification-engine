package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

// A user together with the username from their credential.
@Value.Immutable
public interface AccountModel {
    String getUsername();
    UserModel getUser();
}
