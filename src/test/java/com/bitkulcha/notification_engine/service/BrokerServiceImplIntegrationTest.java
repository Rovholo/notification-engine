package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.repository.BrokerRepository;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:broker-service-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "mqtt.broker-name=not-used-here"
})
@Import(BrokerServiceImpl.class)
class BrokerServiceImplIntegrationTest {

    @Autowired
    private BrokerService brokerService;

    @Autowired
    private BrokerRepository brokerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getBroker_readsBrokerFromTheDatabase() {
        BrokerEntity entity = saveBroker("main", "broker.example.com");
        entityManager.flush();
        entityManager.clear();

        BrokerModel broker = brokerService.getBroker(entity.getId());

        assertThat(broker.getId()).isEqualTo(entity.getId());
        assertThat(broker.getName()).isEqualTo("main");
        assertThat(broker.getServer()).isEqualTo("broker.example.com");
        assertThat(broker.getUsername()).isEqualTo("user");
        assertThat(broker.getPassword()).isEqualTo("secret");
        assertThat(broker.isSecure()).isFalse();
    }

    @Test
    void getBrokerByName_whenNameIsShared_returnsTheOldest() {
        BrokerEntity oldest = saveBroker("main", "first.example.com");
        saveBroker("main", "second.example.com");
        saveBroker("other", "other.example.com");
        entityManager.flush();
        entityManager.clear();

        BrokerModel broker = brokerService.getBrokerByName("main");

        assertThat(broker.getId()).isEqualTo(oldest.getId());
        assertThat(broker.getServer()).isEqualTo("first.example.com");
    }

    private BrokerEntity saveBroker(String name, String server) {
        BrokerEntity entity = new BrokerEntity();
        entity.setName(name);
        entity.setServer(server);
        entity.setUsername("user");
        entity.setPassword("secret");
        entity.setSecure(false);
        return brokerRepository.save(entity);
    }
}
