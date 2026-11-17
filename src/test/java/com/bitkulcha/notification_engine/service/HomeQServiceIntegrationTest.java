package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceConnectionEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.enums.StatusChangeEnum;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    @Test
    void recordStatus_tellsFirstChangedAndRepeatedStatuses() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID garage = homeQService.addDevice(aliceId, house.getId(), null, "Garage", DeviceTypeEnum.GARAGE_DOOR,
                "offline", null, null, null, null, null).getId();
        UUID light = homeQService.addDevice(aliceId, house.getId(), null, "Porch light", DeviceTypeEnum.LIGHT,
                null, null, null, null, null, null).getId();
        entityManager.flush();

        assertThat(homeQService.recordStatus(house.getId(), garage, "closed")).isEqualTo(StatusChangeEnum.FIRST);
        assertThat(homeQService.recordStatus(house.getId(), garage, "closed")).isEqualTo(StatusChangeEnum.UNCHANGED);
        assertThat(homeQService.recordStatus(house.getId(), garage, "open")).isEqualTo(StatusChangeEnum.CHANGED);
        assertThat(homeQService.recordStatus(house.getId(), light, "on")).isEqualTo(StatusChangeEnum.FIRST);
        assertThat(homeQService.recordStatus(UUID.randomUUID(), garage, "closed"))
                .isEqualTo(StatusChangeEnum.UNKNOWN_DEVICE);
        entityManager.clear();

        assertThat(homeQService.findHouse(house.getId()).orElseThrow().getDevices())
                .extracting(d -> d.getStatus().orElse(null))
                .containsExactly("open", "on");
    }

    @Test
    void addDevice_storesALongShellyBoardId() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        String board = "shelly:shellyplus2pm-aabbccddeeff";
        homeQService.addDevice(aliceId, house.getId(), null, "Porch", DeviceTypeEnum.LIGHT, null, null, null, null,
                null, board);
        entityManager.flush();
        entityManager.clear();

        assertThat(homeQService.findHouse(house.getId()).orElseThrow().getDevices().getFirst().getBoardId())
                .contains(board);
    }

    @Test
    void markDeviceReset_putsAnActiveDeviceBackToAddedOnce() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID garage = homeQService.addDevice(aliceId, house.getId(), null, "Garage", DeviceTypeEnum.GARAGE_DOOR,
                "closed", DeviceSetupStatusEnum.ACTIVE, null, null, null, null).getId();
        entityManager.flush();

        assertThat(homeQService.markDeviceReset(house.getId(), garage)).isTrue();
        assertThat(homeQService.markDeviceReset(house.getId(), garage)).isFalse();
        assertThat(homeQService.markDeviceReset(UUID.randomUUID(), garage)).isFalse();
        entityManager.flush();
        entityManager.clear();

        DeviceModel reset = homeQService.findHouse(house.getId()).orElseThrow().getDevices().getFirst();
        assertThat(reset.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ADDED);
        assertThat(reset.getStatus()).contains("offline");
        assertThat(homeQService.recordStatus(house.getId(), garage, "closed")).isEqualTo(StatusChangeEnum.FIRST);
    }

    @Test
    void updateDevice_changesNotificationsOnlyWhenGiven() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        DeviceModel light = homeQService.addDevice(aliceId, house.getId(), null, "Porch light", DeviceTypeEnum.LIGHT,
                null, null, null, null, null, null);
        assertThat(light.notifiesStatus()).isFalse();
        assertThat(light.notifiesConnection()).isFalse();

        DeviceModel enabled = homeQService.updateDevice(aliceId, house.getId(), light.getId(), "Porch light",
                DeviceTypeEnum.LIGHT, DeviceSetupStatusEnum.ACTIVE, true, null);
        DeviceModel renamed = homeQService.updateDevice(aliceId, house.getId(), light.getId(), "Porch",
                DeviceTypeEnum.LIGHT, DeviceSetupStatusEnum.ACTIVE);

        assertThat(enabled.notifiesStatus()).isTrue();
        assertThat(renamed.notifiesStatus()).isTrue();
        assertThat(renamed.notifiesConnection()).isFalse();
    }

    @Test
    void connection_isRecordedForBoardsAndShellys() {
        UUID aliceId = userRepository.save(newUser("Alice")).getId();
        HouseModel house = homeQService.createHouse(aliceId, "Greenwood Manor");
        UUID first = UuidCreator.getTimeOrderedEpoch();
        UUID second = UuidCreator.getTimeOrderedEpoch();
        UUID shelly = UuidCreator.getTimeOrderedEpoch();
        homeQService.addDevice(aliceId, house.getId(), first, "Gate", DeviceTypeEnum.GARAGE_DOOR, "closed",
                DeviceSetupStatusEnum.ACTIVE, null, first, 0, "a1b2c3d4e5f6");
        homeQService.addDevice(aliceId, house.getId(), second, "Porch", DeviceTypeEnum.LIGHT, "on",
                DeviceSetupStatusEnum.ACTIVE, null, first, 1, "a1b2c3d4e5f6");
        homeQService.addDevice(aliceId, house.getId(), shelly, "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed",
                DeviceSetupStatusEnum.ACTIVE, null, null, null, "shelly:abc123");
        entityManager.flush();
        Instant seen = Instant.parse("2026-11-17T08:00:00Z");

        homeQService.markOnline(house.getId(), second, false, seen);
        assertThat(connections(house)).containsEntry(second, DeviceConnectionEnum.ONLINE);
        assertThat(device(house, second).getLastSeenAt()).contains(seen);

        homeQService.markBoardOffline(house.getId(), first);
        assertThat(connections(house))
                .containsEntry(first, DeviceConnectionEnum.OFFLINE)
                .containsEntry(second, DeviceConnectionEnum.OFFLINE);
        assertThat(device(house, second).getStatus()).contains("on");

        homeQService.markOnline(house.getId(), second, true, seen);
        homeQService.markOnline(house.getId(), shelly, true, seen);
        assertThat(connections(house)).containsEntry(second, DeviceConnectionEnum.OFFLINE).doesNotContainKey(shelly);

        homeQService.markOnline(house.getId(), first, true, seen);
        assertThat(connections(house))
                .containsEntry(first, DeviceConnectionEnum.ONLINE)
                .containsEntry(second, DeviceConnectionEnum.ONLINE);

        homeQService.recordShellyConnection(house.getId(), "abc123", false, seen);
        assertThat(connections(house)).containsEntry(shelly, DeviceConnectionEnum.OFFLINE);
        assertThat(device(house, shelly).getLastSeenAt()).isEmpty();
        homeQService.recordShellyConnection(house.getId(), "abc123", true, seen);
        assertThat(connections(house)).containsEntry(shelly, DeviceConnectionEnum.ONLINE);
        assertThat(device(house, shelly).getLastSeenAt()).contains(seen);
    }

    private Map<UUID, DeviceConnectionEnum> connections(HouseModel house) {
        entityManager.clear();
        return homeQService.findHouse(house.getId()).orElseThrow().getDevices().stream()
                .filter(d -> d.getConnection().isPresent())
                .collect(Collectors.toMap(DeviceModel::getId, d -> d.getConnection().orElseThrow()));
    }

    private DeviceModel device(HouseModel house, UUID deviceId) {
        entityManager.clear();
        return homeQService.findHouse(house.getId()).orElseThrow().getDevices().stream()
                .collect(Collectors.toMap(DeviceModel::getId, Function.identity()))
                .get(deviceId);
    }

    private UserEntity newUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }
}
