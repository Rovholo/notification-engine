package com.bitkulcha.notification_engine.domain.model.firebase;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@JsonDeserialize(as = HouseModelImmtbl.class)
@JsonSerialize(as = HouseModelImmtbl.class)
public interface HouseModel extends FirebaseBaseModel<HouseModel> {
    String getName();
    List<String> getOwners();
    List<String> getResidents();
    List<String> getDevices();
}
