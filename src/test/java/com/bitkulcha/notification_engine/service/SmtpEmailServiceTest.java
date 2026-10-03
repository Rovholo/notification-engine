package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.EmailMessageModelImmtbl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailServiceTest {

    @Test
    void sendEmail_sendsToEveryRecipientFromTheConfiguredAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailService emailService = new SmtpEmailService(mailSender, "noreply@example.com");

        emailService.sendEmail(EmailMessageModelImmtbl.builder()
                .addTo("a@b.com", "c@d.com")
                .subject("Door")
                .body("Your door opened")
                .build());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("noreply@example.com");
        assertThat(sent.getTo()).containsExactly("a@b.com", "c@d.com");
        assertThat(sent.getSubject()).isEqualTo("Door");
        assertThat(sent.getText()).isEqualTo("Your door opened");
    }
}
