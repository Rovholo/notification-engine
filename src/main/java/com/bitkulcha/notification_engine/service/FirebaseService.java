package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.BrokerDto;
import com.bitkulcha.notification_engine.dto.HouseDto;
import com.bitkulcha.notification_engine.dto.PushMessageDto;

import java.util.List;

public interface FirebaseService {
    void handleMessage(String topic, Object value);

    void sendMessage(PushMessageDto pushMessage);

    void sendMessage(List<String> topics, PushMessageDto pushMessage);

    void getAllBrokers();

    BrokerDto getBroker(String id);

    HouseDto getHouse(String id);

    void updateHouse(HouseDto houseDto);

    List<String> getUserIds(String value);

    List<HouseDto> getUserHouses(String userId);
}
