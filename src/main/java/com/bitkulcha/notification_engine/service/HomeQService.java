package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;

import java.util.List;
import java.util.UUID;

public interface HomeQService {

    List<HouseModel> getHousesForMember(UUID userId);

    HouseModel createHouse(UUID ownerId, String name);

    DeviceModel addDevice(UUID userId, UUID houseId, String name, DeviceTypeEnum type, String status);
}
