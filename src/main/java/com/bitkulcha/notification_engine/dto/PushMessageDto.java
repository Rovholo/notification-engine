package com.bitkulcha.notification_engine.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = PushMessageDtoImmtbl.class)
@JsonSerialize(as = PushMessageDtoImmtbl.class)
public interface PushMessageDto {
    String getTopic();
    String getTitle();
    String getBody();
    Notification getNotification();

    @Value.Immutable
    @JsonDeserialize(as = NotificationImmtbl.class)
    @JsonSerialize(as = NotificationImmtbl.class)
    interface Notification {
        String getTitle();
        String getBody();
    }
}
