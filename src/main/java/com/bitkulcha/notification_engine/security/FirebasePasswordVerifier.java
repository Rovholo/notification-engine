package com.bitkulcha.notification_engine.security;

public interface FirebasePasswordVerifier {

    boolean verify(String email, String password, String firebaseUid);
}
