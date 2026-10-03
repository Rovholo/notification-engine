package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.repository.BrokerRepository;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class BrokerServiceImpl implements BrokerService {

    private final BrokerRepository brokerRepository;
    private final String defaultBrokerName;

    public BrokerServiceImpl(BrokerRepository brokerRepository,
                             @Value("${mqtt.broker-name}") String defaultBrokerName) {
        this.brokerRepository = brokerRepository;
        this.defaultBrokerName = defaultBrokerName;
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerModel getBroker(UUID brokerId) {
        return brokerRepository.findById(brokerId)
                .map(EntityModelMapper::toModel)
                .orElseThrow(() -> new BrokerNotFoundException("No such broker"));
    }

    // Names are not unique; when several brokers share one, the oldest wins.
    @Override
    @Transactional(readOnly = true)
    public BrokerModel getBrokerByName(String name) {
        List<BrokerEntity> brokers = brokerRepository.findByNameOrderByIdAsc(name);
        if (brokers.size() > 1) {
            log.warn("{} brokers are named '{}', using the oldest ({})", brokers.size(), name, brokers.getFirst().getId());
        }
        return brokers.stream()
                .findFirst()
                .map(EntityModelMapper::toModel)
                .orElseThrow(() -> new BrokerNotFoundException("No broker named " + name));
    }

    @Override
    @Transactional(readOnly = true)
    public BrokerModel getDefaultBroker() {
        return getBrokerByName(defaultBrokerName);
    }
}
