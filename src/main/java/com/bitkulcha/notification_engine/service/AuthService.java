package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.model.CurrentUserResponse;
import com.bitkulcha.notification_engine.model.LoginRequest;
import com.bitkulcha.notification_engine.model.RegisterRequest;

import java.util.UUID;

public interface AuthService {

    AuthTokens register(RegisterRequest request);

    AuthTokens login(LoginRequest request);

    AuthTokens refresh(String refreshToken);

    void logout(String refreshToken);

    CurrentUserResponse getCurrentUser(UUID userId);
}
