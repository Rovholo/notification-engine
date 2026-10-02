package com.bitkulcha.notification_engine.domain.model.firebase;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = UserModelImmtbl.class)
@JsonSerialize(as = UserModelImmtbl.class)
public interface UserModel extends FirebaseBaseModel<UserModel> {
    String getName();
    String getSurname();
    String getEmail();
    String getCell();
}
