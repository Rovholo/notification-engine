package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.HouseDto;

import java.util.List;

public interface HomeQService {

    List<HouseDto> getUserHouses(String userId);

    void addHouseResident(String userId, String homeId);
}
