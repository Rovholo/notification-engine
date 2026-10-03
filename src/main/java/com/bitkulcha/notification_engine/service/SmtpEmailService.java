package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.EmailMessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Profile("!local")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailService(JavaMailSender mailSender, @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendPasswordResetCode(String to, String code, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Your password reset code");
        message.setText("Your password reset code is " + code + ".\n\n"
                + "It expires in " + validFor.toMinutes() + " minutes. "
                + "If you didn't ask to reset your password, you can ignore this email.");
        mailSender.send(message);
    }

    @Override
    public void sendEmail(EmailMessageModel email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.getTo().toArray(String[]::new));
        message.setSubject(email.getSubject());
        message.setText(email.getBody());
        mailSender.send(message);
    }
}
