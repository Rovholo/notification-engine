package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.NotificationImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.firebase.FirebaseBaseModel;
import com.bitkulcha.notification_engine.domain.model.firebase.HouseModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FirebaseService {
    private static final String HOUSES = "houses";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Firestore firestore;
    private final FirebaseMessaging firebaseMessaging;

    public FirebaseService(Firestore firestore, FirebaseMessaging firebaseMessaging) {
        this.firestore = firestore;
        this.firebaseMessaging = firebaseMessaging;
    }

    public void handleMessage(String topic, Object value) {
        try {
            log.debug("Message received : {} {}", topic, value);
            Map<String, Object> map = objectMapper.readValue((String) value, new TypeReference<>() {});
            String[] topicSplit = topic.split("/");
            if ("reaction".equalsIgnoreCase(topicSplit[2]) && map.get("old") != null) {
                HouseModel house = getHouse(topicSplit[1]);
                PushMessageModel messageModel = PushMessageModelImmtbl.builder()
                        .topic(topicSplit[1])
                        .notification(NotificationImmtbl.builder()
                                .title(house.getName())
                                .body("Your " + map.get("name") + " is " + map.get("status"))
                                .build())
                        .build();
                sendMessage(house.getOwners(), messageModel);
            }
        } catch (Exception e) {
            log.error("Error handling message from topic: {} {} \n", topic, value, e);
        }
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

    public HouseModel getHouse(String id) {
        return getDoc(id, HOUSES, HouseModel.class);
    }

    <T extends FirebaseBaseModel<T>> T getDoc(String id, String name, Class<T> valueType) {
        try {
            DocumentReference ref = firestore.collection(name).document(id);
            return objectMapper.convertValue(ref.get().get().getData(), valueType).withId(ref.getId());
        } catch (Exception e) {
            log.error("Error while getting document from Firebase for id: {}", id, e);
            return null;
        }
    }
}
