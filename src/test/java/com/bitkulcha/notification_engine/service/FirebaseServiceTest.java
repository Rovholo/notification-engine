package com.bitkulcha.notification_engine.service;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.bitkulcha.notification_engine.domain.model.NotificationImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModelImmtbl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseServiceTest {

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FirebaseService firebaseService;

    @Test
    void sendMessage_single_sendsPushNotification() throws Exception {
        PushMessageModel message = pushMessage("house-1", "Door", "opened");

        firebaseService.sendMessage(message);

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void sendMessage_single_whenFirebaseMessagingFails_wrapsInRuntimeException() throws Exception {
        PushMessageModel message = pushMessage("house-1", "Door", "opened");
        doThrow(new RuntimeException("fcm unavailable")).when(firebaseMessaging).send(any(Message.class));

        assertThatThrownBy(() -> firebaseService.sendMessage(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error while sending Firebase message");
    }

    @Test
    void sendMessage_multipleTopics_sendsToEachTopic() {
        PushMessageModel message = pushMessage("house-1", "Door", "opened");

        firebaseService.sendMessage(List.of("topic-1", "topic-2"), message);

        verify(firebaseMessaging).sendEachAsync(argThat(messages -> messages.size() == 2));
    }

    @Test
    void sendMessage_withCollapseKey_replacesTheSameKeyOnAndroidIosAndInTheApp() throws Exception {
        PushMessageModel message = PushMessageModelImmtbl.builder()
                .from(pushMessage("house-1", "Door", "opened"))
                .collapseKey("device-1-status")
                .build();

        String json = sent(message);

        assertThat(json)
                .contains("\"tag\":\"device-1-status\"")
                .contains("\"apns-collapse-id\":\"device-1-status\"")
                .contains("\"collapseKey\":\"device-1-status\"");
    }

    @Test
    void sendMessage_withoutCollapseKey_showsSeparately() throws Exception {
        String json = sent(pushMessage("house-1", "Door", "opened"));

        assertThat(json).doesNotContain("tag", "apns-collapse-id", "collapseKey");
    }

    private String sent(PushMessageModel message) throws Exception {
        firebaseService.sendMessage(message);
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(sent.capture());
        return GsonFactory.getDefaultInstance().toString(sent.getValue());
    }

    private PushMessageModel pushMessage(String topic, String title, String body) {
        return PushMessageModelImmtbl.builder()
                .topic(topic)
                .notification(NotificationImmtbl.builder().title(title).body(body).build())
                .build();
    }
}