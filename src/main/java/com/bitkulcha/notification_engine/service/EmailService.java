package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.EmailMessageModel;
import java.time.Duration;

public interface EmailService {

    void sendPasswordResetCode(String to, String code, Duration validFor);

    void sendEmail(EmailMessageModel email);
}
