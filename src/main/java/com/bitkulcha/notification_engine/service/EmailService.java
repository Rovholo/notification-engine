package com.bitkulcha.notification_engine.service;

import java.time.Duration;

public interface EmailService {

    void sendPasswordResetCode(String to, String code, Duration validFor);
}
