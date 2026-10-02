package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the house and device flows against a real database, which mocked repositories can't catch persistence bugs in.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:homeq-service-test;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Import(HomeQServiceImpl.class)
class HomeQServiceImplIntegrationTest {

    @Autowired
    private HomeQService homeQService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createHouseThenAddDevices_isReturnedByGetHousesForMember() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();

        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        DeviceModel garage = homeQService.addDevice(aliceId, house.getId(), "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed");
        homeQService.addDevice(aliceId, house.getId(), "Porch light", DeviceTypeEnum.LIGHT, null);
        entityManager.flush();
        entityManager.clear();

        List<HouseModel> houses = homeQService.getHousesForMember(aliceId);

        assertThat(houses).singleElement().satisfies(loaded -> {
            assertThat(loaded.getId()).isEqualTo(house.getId());
            assertThat(loaded.getOwners()).singleElement()
                    .satisfies(owner -> assertThat(owner.getId()).isEqualTo(aliceId));
            assertThat(loaded.getDevices()).extracting(DeviceModel::getName).containsExactly("Garage", "Porch light");
            assertThat(loaded.getDevices().getFirst().getId()).isEqualTo(garage.getId());
            assertThat(loaded.getDevices().getFirst().getStatus()).contains("closed");
            assertThat(loaded.getDevices().getLast().getStatus()).isEmpty();
        });
    }

    private UserEntity newUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }
}
