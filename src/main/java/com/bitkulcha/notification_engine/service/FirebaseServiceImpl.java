package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
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
            if ("reaction".equalsIgnoreCase(topicSplit[2]) && map.get("old") != null) {
                HouseDto house = getHouse(topicSplit[1]);
                PushMessageDto messageDto = PushMessageDtoImmtbl.builder()
                        .topic(topicSplit[1])
                        .notification(NotificationImmtbl.builder()
                                .title(house.getName())
                                .body("Your " + map.get("name") + " is " + map.get("status"))
                                .build())
                        .build();
                sendMessage(house.getOwners(), messageDto);
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
    public void sendMessage(List<String> topics, PushMessageDto pushMessage) {
        String title = pushMessage.getTitle();
        String body = pushMessage.getBody();
        PushMessageDto.Notification  notification = pushMessage.getNotification();
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

    @Override
    public void getAllBrokers() {
        getDocs(BROKERS, BrokerDto.class);
    }

    @Override
    public BrokerDto getBroker(String id) {
        return getDoc(id, BROKERS, BrokerDto.class);
    }

    @Override
    public HouseDto getHouse(String id) {
        return getDoc(id, HOUSES, HouseDto.class);
    }

    @Override
    public void updateHouse(HouseDto house) {
        updateDoc(HOUSES, house);
    }

    @Override
    public List<String> getUserIds(String value) {
        return getDocIds(USERS, "email", value);
    }

    @Override
    public List<HouseDto> getUserHouses(String userId) {
        return getDocs("owners", userId, HOUSES, HouseDto.class);
    }

    private <T extends FirebaseBaseDto<T>> void addDoc(String name, T data) {
        firestore.collection(name).add(data);
    }

    private <T extends FirebaseBaseDto<T>> void setDoc(String name, T data) {
        firestore.document(name + "/" + data.getId()).set(data);
    }

    private <T extends FirebaseBaseDto<T>> void updateDoc(String name, T data) {
        try {
            String id = data.getId().orElseThrow();
            firestore.document(name + "/" + id).set(data, SetOptions.merge());
        } catch (Exception e) {
            log.error("Error while updating Firebase document", e);
            throw new RuntimeException("Error while updating Firebase document", e);
        }
    }

    <T extends FirebaseBaseDto<T>> T getDoc(String id, String name, Class<T> valueType) {
        try {
            DocumentReference ref = firestore.collection(name).document(id);
            return objectMapper.convertValue(ref.get().get().getData(), valueType).withId(ref.getId());
        } catch (Exception e) {
            log.error("Error while getting document from Firebase for id: {}", id, e);
            return null;
        }
    }

    <T extends FirebaseBaseDto<T>> List<T>  getDocs(String name, Class<T> valueType) {
        try {
            return firestore.collection(name).get().get().getDocuments().stream()
                    .map( (doc) -> objectMapper.convertValue(doc.getData(), valueType).withId(doc.getId()))
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving documents: {} {}", name, e.getMessage());
            return List.of();
        }
    }

    List<String> getDocIds(String name, String type, String value) {
        try {
            return firestore.collection(name).whereEqualTo(type, value).get().get().getDocuments().stream()
                    .map(DocumentSnapshot::getId)
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving document ids: {} {}:{} {}", name, type, value, e.getMessage());
            return List.of();
        }
    }

    <T extends FirebaseBaseDto<T>> List<T> getDocs(String name, String type, String value, Class<T> valueType) {
        try {
            return firestore.collection(name).whereEqualTo(type, value).get().get().getDocuments().stream()
                    .map((doc) -> objectMapper.convertValue(doc.getData(), valueType).withId(doc.getId()))
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving documents: {} {}:{} {}", name, type, value, e.getMessage());
            return List.of();
        }
    }
}
