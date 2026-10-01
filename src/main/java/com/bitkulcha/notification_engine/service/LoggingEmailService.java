package com.bitkulcha.notification_engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Local development has no SMTP server, so reset codes are written to the log instead.
@Slf4j
@Service
@Profile("local")
public class LoggingEmailService implements EmailService {

    @Override
    public void sendPasswordResetCode(String to, String code, Duration validFor) {
        log.info("Password reset code for {}: {} (valid for {} minutes)", to, code, validFor.toMinutes());
    }
}
