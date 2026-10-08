package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FirebaseService {
    private final FirebaseMessaging firebaseMessaging;

    public FirebaseService(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    public void sendMessage(PushMessageModel pushMessage) {
        String title = pushMessage.getTitle();
        String body = pushMessage.getBody();
        PushMessageModel.Notification  notification = pushMessage.getNotification();
        log.info("sendMessage: title={}, body={}, notification={}", title, body, notification);
        try {
            firebaseMessaging.send(toMessage(pushMessage.getTopic(), pushMessage));
            log.debug("push message sent to topic: {}", pushMessage.getTopic());
        } catch (Exception e) {
            log.error("Error while sending Firebase message", e);
            throw new RuntimeException("Error while sending Firebase message", e);
        }
    }

    public void sendMessage(List<String> topics, PushMessageModel pushMessage) {
        String title = pushMessage.getTitle();
        String body = pushMessage.getBody();
        PushMessageModel.Notification  notification = pushMessage.getNotification();
        log.info("sendMessages: title={}, body={}, notification={}", title, body, notification);
        try {
            firebaseMessaging.sendEachAsync(topics.parallelStream()
                    .map(topic -> toMessage(topic, pushMessage))
                    .toList());
            log.debug("push message sent to topics: {}", topics);
        } catch (Exception e) {
            log.error("Error while sending Firebase message", e);
            throw new RuntimeException("Error while sending Firebase message", e);
        }
    }

    /**
     * The collapse key is the Android tag and the iOS collapse id, so the phone replaces a shown notification with the
     * same key even when the app is closed. On Android the app shows notifications itself while it is open, so it gets
     * the key as {@code collapseKey} and uses it as the tag too.
     */
    private Message toMessage(String topic, PushMessageModel pushMessage) {
        PushMessageModel.Notification notification = pushMessage.getNotification();
        Message.Builder message = Message.builder()
                .putData("title", pushMessage.getTitle())
                .putData("body", pushMessage.getBody())
                .setTopic(topic)
                .setNotification(Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getBody())
                        .build());
        pushMessage.getCollapseKey().ifPresent(key -> message
                .putData("collapseKey", key)
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder().setTag(key).build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-collapse-id", key)
                        .setAps(Aps.builder().build())
                        .build()));
        return message.build();
    }
}
