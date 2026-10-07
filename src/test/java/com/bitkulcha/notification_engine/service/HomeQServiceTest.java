package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.exception.DeviceIdConflictException;
import com.bitkulcha.notification_engine.exception.DeviceNotFoundException;
import com.bitkulcha.notification_engine.exception.HouseAccessDeniedException;
import com.bitkulcha.notification_engine.exception.HouseNotFoundException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.exception.InvalidDeviceIdException;
import com.bitkulcha.notification_engine.repository.HouseRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.DeviceEntity;
import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeQServiceTest {

    @Mock
    private HouseRepository houseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HomeQService homeQService;

    @Test
    void getHousesForMember_returnsHouseModels_sortedByName() {
        UserEntity alice = user("Alice");
        HouseEntity zebra = house("Zebra House");
        HouseEntity greenwood = house("Greenwood Manor");
        greenwood.getOwners().add(alice);
        when(houseRepository.findAllByMember(alice.getId())).thenReturn(List.of(zebra, greenwood));

        List<HouseModel> result = homeQService.getHousesForMember(alice.getId());

        assertThat(result).extracting(HouseModel::getId).containsExactly(greenwood.getId(), zebra.getId());
        assertThat(result.getFirst().getOwners()).extracting(UserModel::getId).containsExactly(alice.getId());
    }

    @Test
    void getHousesForMember_whenUserHasNoHouses_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(houseRepository.findAllByMember(userId)).thenReturn(List.of());

        assertThat(homeQService.getHousesForMember(userId)).isEmpty();
    }

    @Test
    void createHouse_makesTheCallerTheOwner_andStripsTheName() {
        UserEntity alice = user("Alice");
        when(userRepository.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(houseRepository.save(any(HouseEntity.class))).then(returnsFirstArg());

        HouseModel result = homeQService.createHouse(alice.getId(), "  Greenwood Manor ");

        assertThat(result.getName()).isEqualTo("Greenwood Manor");
        assertThat(result.getOwners()).extracting(UserModel::getId).containsExactly(alice.getId());
        assertThat(result.getResidents()).isEmpty();
        assertThat(result.getDevices()).isEmpty();
    }

    @Test
    void createHouse_whenUserNoLongerExists_throwsInvalidCredentials() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeQService.createHouse(userId, "Greenwood Manor"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(houseRepository, never()).save(any());
    }

    @Test
    void addDevice_asOwner_addsDeviceToTheHouse() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        DeviceModel result = homeQService.addDevice(alice.getId(), house.getId(), null,
                " Garage ", DeviceTypeEnum.GARAGE_DOOR, "closed", null);

        assertThat(result.getName()).isEqualTo("Garage");
        assertThat(result.getType()).isEqualTo(DeviceTypeEnum.GARAGE_DOOR);
        assertThat(result.getStatus()).contains("closed");
        assertThat(result.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ADDED);
        assertThat(house.getDevices()).singleElement().satisfies(device -> {
            assertThat(device.getId()).isEqualTo(result.getId());
            assertThat(device.getHouse()).isSameAs(house);
        });
    }

    @Test
    void addDevice_asResident_throwsAccessDenied() {
        UserEntity charlie = user("Charlie");
        HouseEntity house = house("Greenwood Manor");
        house.getResidents().add(charlie);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.addDevice(charlie.getId(), house.getId(), null,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, null))
                .isInstanceOf(HouseAccessDeniedException.class);
        assertThat(house.getDevices()).isEmpty();
    }

    @Test
    void addDevice_asNonMember_throwsNotFound() {
        UserEntity dave = user("Dave");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(user("Alice"));
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.addDevice(dave.getId(), house.getId(), null,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, null))
                .isInstanceOf(HouseNotFoundException.class);
        assertThat(house.getDevices()).isEmpty();
    }

    @Test
    void addDevice_whenHouseDoesNotExist_throwsNotFound() {
        UUID houseId = UUID.randomUUID();
        when(houseRepository.findById(houseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeQService.addDevice(UUID.randomUUID(), houseId, null,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, null))
                .isInstanceOf(HouseNotFoundException.class);
    }

    @Test
    void addDevice_withSetupStatus_usesIt() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        DeviceModel result = homeQService.addDevice(alice.getId(), house.getId(), null,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, DeviceSetupStatusEnum.ACTIVE);

        assertThat(result.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
    }

    @Test
    void addDevice_withId_usesIt() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        UUID deviceId = UuidCreator.getTimeOrderedEpoch();
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));
        when(houseRepository.existsDeviceById(deviceId)).thenReturn(false);

        DeviceModel result = homeQService.addDevice(alice.getId(), house.getId(), deviceId,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, DeviceSetupStatusEnum.ACTIVE);

        assertThat(result.getId()).isEqualTo(deviceId);
        assertThat(house.getDevices()).singleElement().satisfies(device -> assertThat(device.getId()).isEqualTo(deviceId));
    }

    @Test
    void addDevice_withIdAlreadyInTheHouse_updatesThatDevice_insteadOfAddingAnother() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        DeviceModel result = homeQService.addDevice(alice.getId(), house.getId(), device.getId(),
                "Main garage", DeviceTypeEnum.GARAGE_DOOR, null, DeviceSetupStatusEnum.ACTIVE);

        assertThat(result.getId()).isEqualTo(device.getId());
        assertThat(house.getDevices()).containsExactly(device);
        assertThat(device.getName()).isEqualTo("Main garage");
        assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
    }

    @Test
    void addDevice_withIdUsedInAnotherHouse_throwsConflict() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        UUID deviceId = UuidCreator.getTimeOrderedEpoch();
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));
        when(houseRepository.existsDeviceById(deviceId)).thenReturn(true);

        assertThatThrownBy(() -> homeQService.addDevice(alice.getId(), house.getId(), deviceId,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, null))
                .isInstanceOf(DeviceIdConflictException.class);
        assertThat(house.getDevices()).isEmpty();
    }

    @Test
    void addDevice_withIdThatIsNotV7_throwsInvalidDeviceId() {
        assertThatThrownBy(() -> homeQService.addDevice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null, null))
                .isInstanceOf(InvalidDeviceIdException.class);
        verify(houseRepository, never()).findById(any());
    }

    @Test
    void activateAddedDevice_whenAdded_marksItActive() {
        HouseEntity house = house("Greenwood Manor");
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThat(homeQService.activateAddedDevice(house.getId(), device.getId())).isTrue();
        assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
    }

    @Test
    void activateAddedDevice_whenInactive_leavesItInactive() {
        HouseEntity house = house("Greenwood Manor");
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        device.setSetupStatus(DeviceSetupStatusEnum.INACTIVE);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThat(homeQService.activateAddedDevice(house.getId(), device.getId())).isFalse();
        assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.INACTIVE);
    }

    @Test
    void activateAddedDevice_whenAlreadyActive_changesNothing() {
        HouseEntity house = house("Greenwood Manor");
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        device.setSetupStatus(DeviceSetupStatusEnum.ACTIVE);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThat(homeQService.activateAddedDevice(house.getId(), device.getId())).isFalse();
    }

    @Test
    void activateAddedDevice_whenDeviceIsNotInTheHouse_returnsFalse() {
        HouseEntity house = house("Greenwood Manor");
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThat(homeQService.activateAddedDevice(house.getId(), UUID.randomUUID())).isFalse();
    }

    @Test
    void findHouse_returnsTheHouseWithItsOwnersAndDevices() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThat(homeQService.findHouse(house.getId())).hasValueSatisfying(found -> {
            assertThat(found.getName()).isEqualTo("Greenwood Manor");
            assertThat(found.getOwners()).extracting(UserModel::getId).containsExactly(alice.getId());
            assertThat(found.getDevices()).extracting(DeviceModel::getName).containsExactly("Garage");
        });
    }

    @Test
    void findHouse_whenMissing_returnsEmpty() {
        UUID houseId = UUID.randomUUID();
        when(houseRepository.findById(houseId)).thenReturn(Optional.empty());

        assertThat(homeQService.findHouse(houseId)).isEmpty();
    }

    @Test
    void updateDevice_asOwner_updatesNameTypeAndSetupStatus_andKeepsStatus() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed");
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        DeviceModel result = homeQService.updateDevice(alice.getId(), house.getId(), device.getId(),
                " Main garage ", DeviceTypeEnum.LIGHT, DeviceSetupStatusEnum.ACTIVE);

        assertThat(result.getId()).isEqualTo(device.getId());
        assertThat(result.getName()).isEqualTo("Main garage");
        assertThat(result.getType()).isEqualTo(DeviceTypeEnum.LIGHT);
        assertThat(result.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
        assertThat(result.getStatus()).contains("closed");
        assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ACTIVE);
    }

    @Test
    void updateDevice_whenDeviceIsNotInTheHouse_throwsDeviceNotFound() {
        UserEntity alice = user("Alice");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(alice);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.updateDevice(alice.getId(), house.getId(), UUID.randomUUID(),
                "Garage", DeviceTypeEnum.GARAGE_DOOR, DeviceSetupStatusEnum.ACTIVE))
                .isInstanceOf(DeviceNotFoundException.class);
    }

    @Test
    void updateDevice_asResident_throwsAccessDenied_andLeavesDeviceUnchanged() {
        UserEntity charlie = user("Charlie");
        HouseEntity house = house("Greenwood Manor");
        house.getResidents().add(charlie);
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.updateDevice(charlie.getId(), house.getId(), device.getId(),
                "Renamed", DeviceTypeEnum.GARAGE_DOOR, DeviceSetupStatusEnum.ACTIVE))
                .isInstanceOf(HouseAccessDeniedException.class);
        assertThat(device.getName()).isEqualTo("Garage");
        assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusEnum.ADDED);
    }

    @Test
    void updateDevice_asNonMember_throwsNotFound() {
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(user("Alice"));
        DeviceEntity device = device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, null);
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.updateDevice(UUID.randomUUID(), house.getId(), device.getId(),
                "Garage", DeviceTypeEnum.GARAGE_DOOR, DeviceSetupStatusEnum.ACTIVE))
                .isInstanceOf(HouseNotFoundException.class);
    }

    private UserEntity user(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }

    private HouseEntity house(String name) {
        HouseEntity house = new HouseEntity();
        house.setName(name);
        return house;
    }

    private DeviceEntity device(HouseEntity house, String name, DeviceTypeEnum type, String status) {
        DeviceEntity device = new DeviceEntity();
        device.setHouse(house);
        device.setName(name);
        device.setType(type);
        device.setStatus(status);
        house.getDevices().add(device);
        return device;
    }
}
