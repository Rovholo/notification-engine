package com.bitkulcha.notification_engine.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = MqttMessageModelImmtbl.class)
@JsonSerialize(as = MqttMessageModelImmtbl.class)
public interface MqttMessageModel {
    String getTopic();
    String getPayload();
}
