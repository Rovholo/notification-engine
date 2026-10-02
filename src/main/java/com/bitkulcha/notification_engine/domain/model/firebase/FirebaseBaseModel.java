package com.bitkulcha.notification_engine.domain.model.firebase;

import java.util.Optional;

public interface FirebaseBaseModel<T extends FirebaseBaseModel<T>> {
    Optional<String> getId();
    T withId(String id);
}
