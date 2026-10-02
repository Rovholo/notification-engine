package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.BrokerModel;

import java.util.UUID;

public interface BrokerService {

    BrokerModel getBroker(UUID brokerId);

    BrokerModel getDefaultBroker();
}
