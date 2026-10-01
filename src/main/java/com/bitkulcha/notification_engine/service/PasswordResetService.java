package com.bitkulcha.notification_engine.service;

public interface PasswordResetService {

    void requestReset(String email);

    void confirmReset(String email, String code, String newPassword);
}
