package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.BrokerApi;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.model.BrokerDto;
import com.bitkulcha.notification_engine.service.BrokerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrokerController implements BrokerApi {

    private final BrokerService brokerService;

    public BrokerController(BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @Override
    public ResponseEntity<BrokerDto> getBroker(String name) {
        BrokerModel broker = name == null || name.isBlank()
                ? brokerService.getDefaultBroker()
                : brokerService.getBrokerByName(name);
        return ResponseEntity.ok(toBroker(broker));
    }

    private static BrokerDto toBroker(BrokerModel broker) {
        return new BrokerDto(broker.getId().toString(), broker.getName(), broker.getServer(), broker.getUsername(), broker.getPassword(),
                broker.isSecure());
    }
}
