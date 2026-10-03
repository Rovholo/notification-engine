package com.bitkulcha.notification_engine.security;

public interface FirebasePasswordVerifier {

    /**
     * Checks the password against Firebase Authentication.
     *
     * @return true only if Firebase accepts the email and password and the account has the given uid
     */
    boolean verify(String email, String password, String firebaseUid);
}
