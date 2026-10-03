package com.bitkulcha.notification_engine.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// The Admin SDK cannot check passwords, so this uses the Identity Toolkit REST API that the Firebase client SDKs use.
@Slf4j
@Component
public class FirebaseRestPasswordVerifier implements FirebasePasswordVerifier {

    private static final String SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={key}";

    private final RestClient restClient = RestClient.create();
    private final String webApiKey;

    public FirebaseRestPasswordVerifier(@Value("${firebase.web-api-key:}") String webApiKey) {
        this.webApiKey = webApiKey;
    }

    @Override
    public boolean verify(String email, String password, String firebaseUid) {
        if (webApiKey.isBlank()) {
            log.error("firebase.web-api-key is not set, so Firebase users cannot be migrated on login");
            return false;
        }
        try {
            SignInResponse response = restClient.post()
                    .uri(SIGN_IN_URL, webApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignInRequest(email, password, false))
                    .retrieve()
                    .body(SignInResponse.class);
            return response != null && firebaseUid.equals(response.localId());
        } catch (HttpClientErrorException e) {
            // Firebase answers 400 for a wrong password, unknown email or disabled account.
            return false;
        }
    }

    private record SignInRequest(String email, String password, boolean returnSecureToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SignInResponse(String localId) {
    }
}
