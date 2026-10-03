package com.bitkulcha.notification_engine.service.mock;

import com.bitkulcha.notification_engine.domain.model.EmailMessageModel;
import com.bitkulcha.notification_engine.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Local development has no SMTP server, so emails are written to the log instead.
@Slf4j
@Service
@Profile("local")
public class MockEmailService implements EmailService {

    @Override
    public void sendPasswordResetCode(String to, String code, Duration validFor) {
        log.info("Password reset code for {}: {} (valid for {} minutes)", to, code, validFor.toMinutes());
    }

    @Override
    public void sendEmail(EmailMessageModel email) {
        log.info("Email to {}: subject={}, body={}", email.getTo(), email.getSubject(), email.getBody());
    }
}
