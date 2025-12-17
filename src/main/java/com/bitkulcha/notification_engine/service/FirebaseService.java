package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.BrokerDto;
import com.bitkulcha.notification_engine.dto.PushMessageDto;

import java.util.List;

public interface FirebaseService {
    void handleMessage(String topic, Object value);

    void sendMessage(PushMessageDto pushMessage);
    List<BrokerDto> getAllBrokers();
}
