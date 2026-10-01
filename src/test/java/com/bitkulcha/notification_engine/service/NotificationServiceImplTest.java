package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.PushMessageDto;
import com.bitkulcha.notification_engine.model.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageChannel;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MessageChannel mqttOutboundChannel;

    @Mock
    private FirebaseService firebaseService;

    private NotificationServiceImpl notificationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(mqttOutboundChannel, firebaseService);
    }

    @Test
    void sendMessage_push_convertsMessageAndForwardsToFirebase() {
        NotificationRequest request = new NotificationRequest(
                NotificationRequest.TypeEnum.PUSH,
                Map.of("topic", "house-1", "notification", Map.of("title", "Door", "body", "opened")));

        NotificationRequest result = notificationService.sendMessage(request);

        assertThat(result).isSameAs(request);
        ArgumentCaptor<PushMessageDto> captor = ArgumentCaptor.forClass(PushMessageDto.class);
        verify(firebaseService).sendMessage(captor.capture());
        PushMessageDto sent = captor.getValue();
        assertThat(sent.getTopic()).isEqualTo("house-1");
        assertThat(sent.getNotification().getTitle()).isEqualTo("Door");
        assertThat(sent.getNotification().getBody()).isEqualTo("opened");
        verifyNoInteractions(mqttOutboundChannel);
    }

    @Test
    void sendMessage_mqtt_doesNotDispatchAnywhere() {
        NotificationRequest request = new NotificationRequest(
                NotificationRequest.TypeEnum.MQTT,
                Map.of("topic", "house-1", "payload", "ping"));

        NotificationRequest result = notificationService.sendMessage(request);

        assertThat(result).isSameAs(request);
        verifyNoInteractions(mqttOutboundChannel, firebaseService);
    }

    @Test
    void sendMessage_unknownType_doesNothing() {
        NotificationRequest request = new NotificationRequest(
                NotificationRequest.TypeEnum.EMAIL,
                Map.of("to", "a@b.com"));

        NotificationRequest result = notificationService.sendMessage(request);

        assertThat(result).isSameAs(request);
        verifyNoInteractions(mqttOutboundChannel, firebaseService);
    }
}