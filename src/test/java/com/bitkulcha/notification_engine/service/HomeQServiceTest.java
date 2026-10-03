package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.exception.HouseAccessDeniedException;
import com.bitkulcha.notification_engine.exception.HouseNotFoundException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.repository.HouseRepository;
import com.bitkulcha.notification_engine.repository.UserRepository;
import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
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

        DeviceModel result = homeQService.addDevice(alice.getId(), house.getId(),
                " Garage ", DeviceTypeEnum.GARAGE_DOOR, "closed");

        assertThat(result.getName()).isEqualTo("Garage");
        assertThat(result.getType()).isEqualTo(DeviceTypeEnum.GARAGE_DOOR);
        assertThat(result.getStatus()).contains("closed");
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

        assertThatThrownBy(() -> homeQService.addDevice(charlie.getId(), house.getId(),
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null))
                .isInstanceOf(HouseAccessDeniedException.class);
        assertThat(house.getDevices()).isEmpty();
    }

    @Test
    void addDevice_asNonMember_throwsNotFound() {
        UserEntity dave = user("Dave");
        HouseEntity house = house("Greenwood Manor");
        house.getOwners().add(user("Alice"));
        when(houseRepository.findById(house.getId())).thenReturn(Optional.of(house));

        assertThatThrownBy(() -> homeQService.addDevice(dave.getId(), house.getId(),
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null))
                .isInstanceOf(HouseNotFoundException.class);
        assertThat(house.getDevices()).isEmpty();
    }

    @Test
    void addDevice_whenHouseDoesNotExist_throwsNotFound() {
        UUID houseId = UUID.randomUUID();
        when(houseRepository.findById(houseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> homeQService.addDevice(UUID.randomUUID(), houseId,
                "Garage", DeviceTypeEnum.GARAGE_DOOR, null))
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
}
