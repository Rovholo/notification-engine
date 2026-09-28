package com.bitkulcha.notification_engine.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = UserDtoImmtbl.class)
@JsonSerialize(as = UserDtoImmtbl.class)
public interface UserDto extends FirebaseBaseDto<UserDto> {
    String getName();
    String getSurname();
    String getEmail();
    String getCell();
}
