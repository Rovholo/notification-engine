package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

@Value.Immutable
public interface AuthTokensModel {
    String getAccessToken();
    String getRefreshToken();
}
