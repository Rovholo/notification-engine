package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.domain.model.EmailMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.model.NotificationRequestDto;
import com.bitkulcha.notification_engine.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void sendMessage_push_convertsMessageToModelAndSendsIt() {
        NotificationRequestDto request = new NotificationRequestDto(
                NotificationRequestDto.TypeEnum.PUSH,
                Map.of("topic", "house-1", "notification", Map.of("title", "Door", "body", "opened")));

        ResponseEntity<NotificationRequestDto> response = notificationController.sendMessage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(request);
        ArgumentCaptor<PushMessageModel> captor = ArgumentCaptor.forClass(PushMessageModel.class);
        verify(notificationService).sendPushMessage(captor.capture());
        PushMessageModel sent = captor.getValue();
        assertThat(sent.getTopic()).isEqualTo("house-1");
        assertThat(sent.getNotification().getTitle()).isEqualTo("Door");
        assertThat(sent.getNotification().getBody()).isEqualTo("opened");
    }

    @Test
    void sendMessage_mqtt_isAcceptedButNotSent() {
        NotificationRequestDto request = new NotificationRequestDto(
                NotificationRequestDto.TypeEnum.MQTT, Map.of("topic", "house-1", "payload", "ping"));

        ResponseEntity<NotificationRequestDto> response = notificationController.sendMessage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(request);
        verifyNoInteractions(notificationService);
    }

    @Test
    void sendMessage_email_convertsMessageToModelAndSendsIt() {
        NotificationRequestDto request = new NotificationRequestDto(
                NotificationRequestDto.TypeEnum.EMAIL,
                Map.of("to", List.of("a@b.com", "c@d.com"), "subject", "Door", "body", "Your door opened"));

        ResponseEntity<NotificationRequestDto> response = notificationController.sendMessage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(request);
        ArgumentCaptor<EmailMessageModel> captor = ArgumentCaptor.forClass(EmailMessageModel.class);
        verify(notificationService).sendEmailMessage(captor.capture());
        EmailMessageModel sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("a@b.com", "c@d.com");
        assertThat(sent.getSubject()).isEqualTo("Door");
        assertThat(sent.getBody()).isEqualTo("Your door opened");
    }
}
