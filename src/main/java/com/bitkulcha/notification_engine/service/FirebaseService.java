package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
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
            firebaseMessaging.send(Message.builder()
                    .putData("title", title)
                    .putData("body", body)
                    .setTopic(pushMessage.getTopic())
                    .setNotification(Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getBody())
                            .build())
                    .build());
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
            firebaseMessaging.sendEachAsync(topics.parallelStream().map(topic -> Message.builder()
                    .putData("title", title)
                    .putData("body", body)
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getBody())
                            .build())
                    .build()).toList());
            log.debug("push message sent to topics: {}", topics);
        } catch (Exception e) {
            log.error("Error while sending Firebase message", e);
            throw new RuntimeException("Error while sending Firebase message", e);
        }
    }
}
