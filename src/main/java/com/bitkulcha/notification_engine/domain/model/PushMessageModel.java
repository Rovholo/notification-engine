package com.bitkulcha.notification_engine.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = PushMessageModelImmtbl.class)
@JsonSerialize(as = PushMessageModelImmtbl.class)
public interface PushMessageModel {
    String getTopic();
    Notification getNotification();
    default String getTitle() {
        return "";
    }
    default String getBody() {
        return "";
    }

    @Value.Immutable
    @JsonDeserialize(as = NotificationImmtbl.class)
    @JsonSerialize(as = NotificationImmtbl.class)
    interface Notification {
        String getTitle();
        String getBody();
    }
}
