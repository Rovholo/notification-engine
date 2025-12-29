package com.bitkulcha.notification_engine.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Configuration
@Profile("!test")
public class FirebaseConfig {

    @Value("${firebase.credential}")
    private String firebaseCredential;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(getCredentials())
                    .build());
        }

        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    @Bean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp,"homeq");
    }

    private GoogleCredentials getCredentials() throws IOException {
        try {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(firebaseCredential.getBytes()));
        } catch (IOException e) {
            return GoogleCredentials.fromStream(new ClassPathResource(firebaseCredential).getInputStream());
        }
    }

}
