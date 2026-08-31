package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.BrokerDto;
import com.bitkulcha.notification_engine.dto.NotificationImmtbl;
import com.bitkulcha.notification_engine.dto.PushMessageDto;
import com.bitkulcha.notification_engine.dto.PushMessageDtoImmtbl;
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
public class FirebaseServiceImpl implements FirebaseService {
    private static final String BROKERS = "brokers";
    private static final String USERS = "users";
    private static final String USER_META = "user_meta";
    private static final String HOUSES = "houses";
    private static final String HOUSE_DEVICES = "house_devices";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Firestore firestore;
    private final FirebaseMessaging firebaseMessaging;

    public FirebaseServiceImpl(Firestore firestore, FirebaseMessaging firebaseMessaging) {
        this.firestore = firestore;
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public void handleMessage(String topic, Object value) {
        try {
            log.debug("Message received : {} {}", topic, value);
            Map<String, Object> map = objectMapper.readValue((String) value, new TypeReference<>() {});
            String[] topicSplit = topic.split("/");
            if (topicSplit.length > 2
                    && topicSplit[2].equalsIgnoreCase("reaction")
                    && map.get("old") != null) {
                PushMessageDto messageDto = PushMessageDtoImmtbl.builder()
                        .topic(topicSplit[1])
                        .title("")
                        .body("")
                        .notification(NotificationImmtbl.builder()
                                .title((String) map.get("name"))
                                .body("Your " + map.get("name") + " is " + map.get("status"))
                                .build())
                        .build();
                sendMessage(messageDto);
            }
        } catch (Exception e) {
            log.error("Error handling message from topic: {} {} \n", topic, value, e);
        }
    }

    @Override
    public void sendMessage(PushMessageDto pushMessage) {
        String title = pushMessage.getTitle();
        String body = pushMessage.getBody();
        PushMessageDto.Notification  notification = pushMessage.getNotification();
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

    @Override
    public List<BrokerDto> getAllBrokers() {
        return getDocs(BROKERS, BrokerDto.class);
    }

    @Override
    public BrokerDto getBroker(String id) {
        return getDoc(id, BROKERS, BrokerDto.class);
    }

    public <T> T getDoc(String id, String name, Class<T> valueType) {
        try {
            DocumentReference ref = firestore.collection(name).document(id);
            return objectMapper.convertValue(ref.get().get().getData(), valueType);
        } catch (Exception e) {
            log.error("Error while getting document from Firebase for id: {}", id, e);
            return null;
        }
    }

    private <T> List<T>  getDocs(String name, Class<T> valueType) {
        try {
            return firestore.collection(name).get().get().getDocuments().stream()
                    .map( (doc) -> objectMapper.convertValue(doc.getData(), valueType))
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving documents: {}", e.getMessage());
            return List.of();
        }
    }
}
