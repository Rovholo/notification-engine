package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.BrokerDto;
import com.bitkulcha.notification_engine.dto.NotificationImmtbl;
import com.bitkulcha.notification_engine.dto.PushMessageDto;
import com.bitkulcha.notification_engine.dto.PushMessageDtoImmtbl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FirebaseServiceImpl implements FirebaseService {

    private static final Logger log = LogManager.getLogger(FirebaseServiceImpl.class);
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
            log.debug("Message received from topic: {} {}", topic, value);
            Map map = objectMapper.readValue((String) value, Map.class);
            if (map.get("old") != null) {
                PushMessageDto messageDto = PushMessageDtoImmtbl.builder()
                        .topic(topic.split("/")[1])
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
            log.error("Error handling message from topic: {} {} \n {}", topic,value, e);
        }
    }

    @Override
    public void sendMessage(PushMessageDto pushMessage) {
        try {
            Message msg = Message.builder()
                    .putData("title", pushMessage.getTitle())
                    .putData("body", pushMessage.getBody())
                    .setTopic(pushMessage.getTopic())
                    .setNotification(Notification.builder()
                            .setTitle(pushMessage.getNotification().getTitle())
                            .setBody(pushMessage.getNotification().getBody())
                            .build())
                    .build();
            firebaseMessaging.send(msg);
        } catch (Exception e) {
            log.error("Error while sending Firebase message", e);
            throw new RuntimeException("Error while sending Firebase message", e);
        }
    }

    @Override
    public List<BrokerDto> getAllBrokers() {
        return getDocs(BROKERS, BrokerDto.class);
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
