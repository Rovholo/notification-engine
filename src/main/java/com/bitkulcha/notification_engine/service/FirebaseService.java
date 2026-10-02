package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.firebase.BrokerModel;
import com.bitkulcha.notification_engine.domain.model.firebase.HouseModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;

import java.util.List;

public interface FirebaseService {
    void handleMessage(String topic, Object value);

    void sendMessage(PushMessageModel pushMessage);

    void sendMessage(List<String> topics, PushMessageModel pushMessage);

    void getAllBrokers();

    BrokerModel getBroker(String id);

    HouseModel getHouse(String id);

    void updateHouse(HouseModel houseModel);

    List<String> getUserIds(String value);

    List<HouseModel> getUserHouses(String userId);
}
