package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.domain.model.BrokerModelImmtbl;
import com.bitkulcha.notification_engine.model.BrokerDto;
import com.bitkulcha.notification_engine.service.BrokerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerControllerTest {

    @Mock
    private BrokerService brokerService;

    @InjectMocks
    private BrokerController brokerController;

    @Test
    void getBroker_withName_mapsThatBrokerToResponse() {
        BrokerModel broker = broker();
        when(brokerService.getBrokerByName("main")).thenReturn(broker);

        ResponseEntity<BrokerDto> response = brokerController.getBroker("main");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(broker.getId().toString());
        assertThat(response.getBody().getName()).isEqualTo("main");
        assertThat(response.getBody().getServer()).isEqualTo("broker.example.com");
        assertThat(response.getBody().getUsername()).isEqualTo("user");
        assertThat(response.getBody().getPassword()).isEqualTo("secret");
        assertThat(response.getBody().getSecure()).isTrue();
        verify(brokerService, never()).getDefaultBroker();
    }

    @Test
    void getBroker_withoutName_returnsDefaultBroker() {
        BrokerModel broker = broker();
        when(brokerService.getDefaultBroker()).thenReturn(broker);

        ResponseEntity<BrokerDto> response = brokerController.getBroker(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(broker.getId().toString());
        verify(brokerService, never()).getBrokerByName(any());
    }

    @Test
    void getBroker_withBlankName_returnsDefaultBroker() {
        BrokerModel broker = broker();
        when(brokerService.getDefaultBroker()).thenReturn(broker);

        ResponseEntity<BrokerDto> response = brokerController.getBroker(" ");

        assertThat(response.getBody().getId()).isEqualTo(broker.getId().toString());
        verify(brokerService, never()).getBrokerByName(any());
    }

    private BrokerModel broker() {
        return BrokerModelImmtbl.builder()
                .id(UUID.randomUUID())
                .name("main")
                .server("broker.example.com")
                .username("user")
                .password("secret")
                .isSecure(true)
                .build();
    }
}
