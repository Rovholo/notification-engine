package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.exception.BrokerNotFoundException;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.repository.BrokerRepository;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerServiceImplTest {

    @Mock
    private BrokerRepository brokerRepository;

    @Test
    void getBroker_returnsBrokerModel() {
        BrokerEntity entity = brokerEntity();
        when(brokerRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        BrokerModel broker = service("unused").getBroker(entity.getId());

        assertThat(broker.getId()).isEqualTo(entity.getId());
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
    void getDefaultBroker_looksUpConfiguredId() {
        BrokerEntity entity = brokerEntity();
        when(brokerRepository.findById(entity.getId())).thenReturn(Optional.of(entity));

        BrokerModel broker = service(entity.getId().toString()).getDefaultBroker();

        assertThat(broker.getId()).isEqualTo(entity.getId());
    }

    @Test
    void getDefaultBroker_whenConfiguredIdIsNotAUuid_throwsNotFound() {
        assertThatThrownBy(() -> service("test").getDefaultBroker())
                .isInstanceOf(BrokerNotFoundException.class);
        verify(brokerRepository, never()).findById(any());
    }

    private BrokerServiceImpl service(String defaultBrokerId) {
        return new BrokerServiceImpl(brokerRepository, defaultBrokerId);
    }

    private BrokerEntity brokerEntity() {
        BrokerEntity broker = new BrokerEntity();
        broker.setServer("broker.example.com");
        broker.setUsername("user");
        broker.setPassword("secret");
        broker.setSecure(true);
        return broker;
    }
}
