package com.bitkulcha.notification_engine.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonDeserialize(as = EmailMessageModelImmtbl.class)
@JsonSerialize(as = EmailMessageModelImmtbl.class)
public interface EmailMessageModel {
    List<String> getTo();
    String getSubject();
    String getBody();
}
