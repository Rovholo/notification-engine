package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.repository.BrokerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class BrokerServiceImpl implements BrokerService {

    private final BrokerRepository brokerRepository;
    private final String defaultBrokerId;

    public BrokerServiceImpl(BrokerRepository brokerRepository, @Value("${mqtt.broker-id}") String defaultBrokerId) {
        this.brokerRepository = brokerRepository;
        this.defaultBrokerId = defaultBrokerId;
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerModel getBroker(UUID brokerId) {
        return brokerRepository.findById(brokerId)
                .map(EntityModelMapper::toModel)
                .orElseThrow(() -> new BrokerNotFoundException("No such broker"));
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerModel getDefaultBroker() {
        UUID brokerId;
        try {
            brokerId = UUID.fromString(defaultBrokerId);
        } catch (IllegalArgumentException e) {
            // Parsed per request rather than at startup because MqttConfig still reads this id from Firebase,
            // where it need not be a UUID.
            log.warn("mqtt.broker-id '{}' is not a UUID, so it cannot match a row in the brokers table", defaultBrokerId);
            throw new BrokerNotFoundException("No default broker configured");
        }
        return getBroker(brokerId);
    }
}
