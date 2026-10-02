package com.bitkulcha.notification_engine.domain.model;

import org.immutables.value.Value;

import java.util.List;
import java.util.UUID;

@Value.Immutable
public interface HouseModel {
    UUID getId();
    String getName();
    List<UserModel> getOwners();
    List<UserModel> getResidents();
    List<DeviceModel> getDevices();
}
