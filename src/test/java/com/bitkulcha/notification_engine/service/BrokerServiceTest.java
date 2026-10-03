package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.repository.BrokerRepository;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerServiceTest {

    @Mock
    private BrokerRepository brokerRepository;

    @Test
    void getBroker_returnsBrokerModel() {
        BrokerEntity entity = brokerEntity("main");
        when(brokerRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        BrokerModel broker = service("unused").getBroker(entity.getId());

        assertThat(broker.getId()).isEqualTo(entity.getId());
        assertThat(broker.getName()).isEqualTo("main");
        assertThat(broker.getServer()).isEqualTo("broker.example.com");
    }

    @Test
    void getBroker_whenMissing_throwsNotFound() {
        UUID brokerId = UUID.randomUUID();
        when(brokerRepository.findById(brokerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("unused").getBroker(brokerId))
                .isInstanceOf(BrokerNotFoundException.class);
    }

    @Test
    void getBrokerByName_returnsFirstMatch() {
        BrokerEntity first = brokerEntity("main");
        BrokerEntity second = brokerEntity("main");
        when(brokerRepository.findByNameOrderByIdAsc("main")).thenReturn(List.of(first, second));

        BrokerModel broker = service("unused").getBrokerByName("main");

        assertThat(broker.getId()).isEqualTo(first.getId());
    }

    @Test
    void getBrokerByName_whenNoMatch_throwsNotFound() {
        when(brokerRepository.findByNameOrderByIdAsc("missing")).thenReturn(List.of());

        assertThatThrownBy(() -> service("unused").getBrokerByName("missing"))
                .isInstanceOf(BrokerNotFoundException.class);
    }

    @Test
    void getDefaultBroker_looksUpConfiguredName() {
        BrokerEntity entity = brokerEntity("main");
        when(brokerRepository.findByNameOrderByIdAsc("main")).thenReturn(List.of(entity));

        BrokerModel broker = service("main").getDefaultBroker();

        assertThat(broker.getId()).isEqualTo(entity.getId());
    }

    private BrokerService service(String defaultBrokerName) {
        return new BrokerService(brokerRepository, defaultBrokerName);
    }

    private BrokerEntity brokerEntity(String name) {
        BrokerEntity broker = new BrokerEntity();
        broker.setName(name);
        broker.setServer("broker.example.com");
        broker.setUsername("user");
        broker.setPassword("secret");
        broker.setSecure(true);
        return broker;
    }
}
