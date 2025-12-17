package com.bitkulcha.notification_engine.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = MqttMessageDtoImmtbl.class)
@JsonSerialize(as = MqttMessageDtoImmtbl.class)
public interface MqttMessageDto {
    String getTopic();
    String getPayload();
}
