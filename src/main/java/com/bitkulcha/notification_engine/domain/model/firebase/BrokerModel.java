package com.bitkulcha.notification_engine.domain.model.firebase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = BrokerModelImmtbl.class)
@JsonSerialize(as = BrokerModelImmtbl.class)
public interface BrokerModel extends FirebaseBaseModel<BrokerModel> {
    String getUsername();
    String getPassword();
    String getServer();
    @JsonProperty("secure")
    Boolean isSecure();
}
