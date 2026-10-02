package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AuthTokensModel;
import com.bitkulcha.notification_engine.domain.model.RegistrationModel;

import java.util.UUID;

public interface AuthService {

    AuthTokensModel register(RegistrationModel registration);

    AuthTokensModel login(String username, String password);

    AuthTokensModel refresh(String refreshToken);

    void logout(String refreshToken);

    AccountModel getCurrentUser(UUID userId);
}
