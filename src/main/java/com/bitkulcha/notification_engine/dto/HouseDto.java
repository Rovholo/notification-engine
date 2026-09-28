package com.bitkulcha.notification_engine.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonDeserialize(as = HouseDtoImmtbl.class)
@JsonSerialize(as = HouseDtoImmtbl.class)
public interface HouseDto extends FirebaseBaseDto<HouseDto> {
    String getName();
    List<String> getOwners();
    List<String> getResidents();
    List<String> getDevices();
}
