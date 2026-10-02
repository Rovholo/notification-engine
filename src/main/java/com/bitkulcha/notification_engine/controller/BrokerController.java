package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.BrokerApi;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.model.BrokerDto;
import com.bitkulcha.notification_engine.service.BrokerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BrokerController implements BrokerApi {

    private final BrokerService brokerService;

    public BrokerController(BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @Override
    public ResponseEntity<BrokerDto> getBroker(UUID brokerId) {
        BrokerModel broker = brokerId == null ? brokerService.getDefaultBroker() : brokerService.getBroker(brokerId);
        return ResponseEntity.ok(toBroker(broker));
    }

    private static BrokerDto toBroker(BrokerModel broker) {
        return new BrokerDto(broker.getId().toString(), broker.getServer(), broker.getUsername(), broker.getPassword(),
                broker.isSecure());
    }
}
