package com.bitkulcha.notification_engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = BrokerDtoImmtbl.class)
@JsonSerialize(as = BrokerDtoImmtbl.class)
public interface BrokerDto {
    String getUsername();
    String getPassword();
    String getServer();
    @JsonProperty("secure")
    Boolean isSecure();
}
