package com.bitkulcha.notification_engine.dto;

import java.util.Optional;

public interface FirebaseBaseDto<T extends FirebaseBaseDto<T>> {
    Optional<String> getId();
    T withId(String id);
}
