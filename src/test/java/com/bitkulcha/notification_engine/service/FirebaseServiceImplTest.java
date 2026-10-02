package com.bitkulcha.notification_engine.service;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.bitkulcha.notification_engine.domain.model.firebase.HouseModel;
import com.bitkulcha.notification_engine.domain.model.firebase.HouseModelImmtbl;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirebaseServiceImplTest {

    @Mock
    private Firestore firestore;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FirebaseServiceImpl firebaseService;

    @Test
    void getHouse_mapsFirestoreDocumentToModel() {
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);

        when(firestore.collection("houses")).thenReturn(collection);
        when(collection.document("house-1")).thenReturn(docRef);
        when(docRef.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(docRef.getId()).thenReturn("house-1");
        when(snapshot.getData()).thenReturn(Map.of(
                "name", "Greenwood Manor",
                "owners", List.of("owner-1"),
                "residents", List.of("resident-1"),
                "devices", List.of("device-1")));

        HouseModel house = firebaseService.getHouse("house-1");

        assertThat(house.getId()).contains("house-1");
        assertThat(house.getName()).isEqualTo("Greenwood Manor");
        assertThat(house.getOwners()).containsExactly("owner-1");
        assertThat(house.getResidents()).containsExactly("resident-1");
    }

    @Test
    void getHouse_whenFirestoreFails_returnsNull() {
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference docRef = mock(DocumentReference.class);

        when(firestore.collection("houses")).thenReturn(collection);
        when(collection.document("house-1")).thenReturn(docRef);
        when(docRef.get()).thenReturn(ApiFutures.immediateFailedFuture(new RuntimeException("offline")));

        HouseModel house = firebaseService.getHouse("house-1");

        assertThat(house).isNull();
    }

    @Test
    void getUserIds_queriesUsersCollectionByEmail() {
        CollectionReference collection = mock(CollectionReference.class);
        Query query = mock(Query.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);

        when(firestore.collection("users")).thenReturn(collection);
        when(collection.whereEqualTo("email", "alice@example.com")).thenReturn(query);
        when(query.get()).thenReturn(ApiFutures.immediateFuture(querySnapshot));
        when(querySnapshot.getDocuments()).thenReturn(List.of(doc));
        when(doc.getId()).thenReturn("user-1");

        List<String> ids = firebaseService.getUserIds("alice@example.com");

        assertThat(ids).containsExactly("user-1");
    }

    @Test
    void updateHouse_mergesUpdateIntoDocumentAtItsId() {
        DocumentReference docRef = mock(DocumentReference.class);
        when(firestore.document(anyString())).thenReturn(docRef);

        HouseModel house = HouseModelImmtbl.builder()
                .id("house-1")
                .name("Greenwood Manor")
                .owners(List.of("owner-1"))
                .residents(List.of("resident-1"))
                .devices(List.of())
                .build();

        firebaseService.updateHouse(house);

        verify(firestore).document("houses/house-1");
        ArgumentCaptor<Object> dataCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<SetOptions> optionsCaptor = ArgumentCaptor.forClass(SetOptions.class);
        verify(docRef).set(dataCaptor.capture(), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue()).isEqualTo(SetOptions.merge());
        HouseModel persisted = (HouseModel) dataCaptor.getValue();
        assertThat(persisted.getId()).contains("house-1");
        assertThat(persisted.getName()).isEqualTo("Greenwood Manor");
    }

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

    private PushMessageModel pushMessage(String topic, String title, String body) {
        return PushMessageModelImmtbl.builder()
                .topic(topic)
                .notification(NotificationImmtbl.builder().title(title).body(body).build())
                .build();
    }
}