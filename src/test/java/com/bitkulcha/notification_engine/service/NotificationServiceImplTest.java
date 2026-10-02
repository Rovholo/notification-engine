package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.NotificationImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModelImmtbl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageChannel;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private MessageChannel mqttOutboundChannel;

    @Mock
    private FirebaseService firebaseService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(mqttOutboundChannel, firebaseService);
    }

    @Test
    void sendPushMessage_forwardsToFirebase() {
        PushMessageModel message = PushMessageModelImmtbl.builder()
                .topic("house-1")
                .notification(NotificationImmtbl.builder().title("Door").body("opened").build())
                .build();

        notificationService.sendPushMessage(message);

        verify(firebaseService).sendMessage(message);
        verifyNoInteractions(mqttOutboundChannel);
    }
}
