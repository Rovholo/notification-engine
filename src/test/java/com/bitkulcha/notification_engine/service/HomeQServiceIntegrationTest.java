package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.exception.HouseAccessDeniedException;
import com.bitkulcha.notification_engine.exception.HouseMemberConflictException;
import com.bitkulcha.notification_engine.exception.UserNotFoundException;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.github.f4b6a3.uuid.UuidCreator;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:homeq-service-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Import(HomeQService.class)
class HomeQServiceIntegrationTest {

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
        DeviceModel garage = homeQService.addDevice(aliceId, house.getId(), null, "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed", null, null, null, null, null);
        homeQService.addDevice(aliceId, house.getId(), null, "Porch light", DeviceTypeEnum.LIGHT, null, null, null, null, null, null);
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
            assertThat(loaded.getDevices()).extracting(DeviceModel::getSetupStatus)
                    .containsOnly(DeviceSetupStatusEnum.ADDED);
        });
    }

    @Test
    void updateDevice_persistsSetupStatus() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        DeviceModel garage = homeQService.addDevice(aliceId, house.getId(), null, "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed", null, null, null, null, null);
        entityManager.flush();
        entityManager.clear();

        homeQService.updateDevice(aliceId, house.getId(), garage.getId(), "Main garage", DeviceTypeEnum.GARAGE_DOOR,
                DeviceSetupStatusEnum.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        DeviceModel loaded = homeQService.getHousesForMember(aliceId).getFirst().getDevices().getFirst();
        assertThat(loaded.getName()).isEqualTo("Main garage");
        assertThat(loaded.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
        assertThat(loaded.getStatus()).contains("closed");
    }

    @Test
    void addDevice_withId_thenRetryWithSameId_persistsOneDevice() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID deviceId = UuidCreator.getTimeOrderedEpoch();
        homeQService.addDevice(aliceId, house.getId(), deviceId, "Garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, null, null, null, null);
        entityManager.flush();
        entityManager.clear();

        homeQService.addDevice(aliceId, house.getId(), deviceId, "Main garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, null, null, null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(homeQService.getHousesForMember(aliceId).getFirst().getDevices()).singleElement().satisfies(loaded -> {
            assertThat(loaded.getId()).isEqualTo(deviceId);
            assertThat(loaded.getName()).isEqualTo("Main garage");
            assertThat(loaded.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
        });
    }

    @Test
    void addDevice_retryWithoutSecret_keepsTheSecretTheDeviceWasSetUpWith() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID deviceId = UuidCreator.getTimeOrderedEpoch();
        String secret = "a".repeat(64);
        homeQService.addDevice(aliceId, house.getId(), deviceId, "Garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, secret, null, null, null);
        entityManager.flush();
        entityManager.clear();

        homeQService.addDevice(aliceId, house.getId(), deviceId, "Garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, null, null, null, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(homeQService.getHousesForMember(aliceId).getFirst().getDevices()).singleElement()
                .satisfies(loaded -> assertThat(loaded.getSecret()).contains(secret));
    }

    @Test
    void claimDeviceSecret_setsTheFirstSecretAndKeepsItForLaterClaims() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID deviceId = UuidCreator.getTimeOrderedEpoch();
        homeQService.addDevice(aliceId, house.getId(), deviceId, "Garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, null, null, null, null);
        entityManager.flush();
        entityManager.clear();

        DeviceModel first = homeQService.claimDeviceSecret(aliceId, house.getId(), deviceId, "c".repeat(64));
        entityManager.flush();
        entityManager.clear();
        DeviceModel second = homeQService.claimDeviceSecret(aliceId, house.getId(), deviceId, "d".repeat(64));

        assertThat(first.getSecret()).contains("c".repeat(64));
        assertThat(second.getSecret()).contains("c".repeat(64));
    }

    @Test
    void addResident_byEmailIgnoringCaseAndSpaces_givesThemTheHouseAndItsSecrets() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        UUID charlieId = userRepository.save(newUser("Charlie")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        homeQService.addDevice(aliceId, house.getId(), null, "Garage", DeviceTypeEnum.GARAGE_DOOR, null,
                DeviceSetupStatusEnum.ACTIVE, "a".repeat(64), null, null, null);
        entityManager.flush();
        entityManager.clear();

        UserModel added = homeQService.addResident(aliceId, house.getId(), "  Charlie@Example.com ");
        entityManager.flush();
        entityManager.clear();

        assertThat(added.getId()).isEqualTo(charlieId);
        assertThat(homeQService.getHousesForMember(charlieId)).singleElement().satisfies(loaded -> {
            assertThat(loaded.getResidents()).extracting(UserModel::getId).containsExactly(charlieId);
            assertThat(loaded.getDevices()).singleElement()
                    .satisfies(device -> assertThat(device.getSecret()).contains("a".repeat(64)));
        });
    }

    @Test
    void addResident_rejectsUnknownEmailsExistingMembersAndResidents() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        UUID charlieId = userRepository.save(newUser("Charlie")).getId();
        userRepository.save(newUser("Dana"));
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        homeQService.addResident(aliceId, house.getId(), "charlie@example.com");
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> homeQService.addResident(aliceId, house.getId(), "nobody@example.com"))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> homeQService.addResident(aliceId, house.getId(), "alice@example.com"))
                .isInstanceOf(HouseMemberConflictException.class);
        assertThatThrownBy(() -> homeQService.addResident(aliceId, house.getId(), "charlie@example.com"))
                .isInstanceOf(HouseMemberConflictException.class);
        assertThatThrownBy(() -> homeQService.addResident(charlieId, house.getId(), "dana@example.com"))
                .isInstanceOf(HouseAccessDeniedException.class);
    }

    @Test
    void removeResident_takesTheHouseAwayFromThem() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        UUID charlieId = userRepository.save(newUser("Charlie")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        homeQService.addResident(aliceId, house.getId(), "charlie@example.com");
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> homeQService.removeResident(charlieId, house.getId(), charlieId))
                .isInstanceOf(HouseAccessDeniedException.class);
        homeQService.removeResident(aliceId, house.getId(), charlieId);
        entityManager.flush();
        entityManager.clear();

        assertThat(homeQService.getHousesForMember(charlieId)).isEmpty();
        assertThat(homeQService.getHousesForMember(aliceId).getFirst().getResidents()).isEmpty();
        assertThatThrownBy(() -> homeQService.removeResident(aliceId, house.getId(), charlieId))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> homeQService.removeResident(aliceId, house.getId(), aliceId))
                .isInstanceOf(UserNotFoundException.class);
    }

    private UserEntity newUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }
}
