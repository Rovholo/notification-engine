package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.DeviceModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.HouseModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.domain.model.UserModelImmtbl;
import com.bitkulcha.notification_engine.model.CreateDeviceRequestDto;
import com.bitkulcha.notification_engine.model.CreateHouseRequestDto;
import com.bitkulcha.notification_engine.model.DeviceDto;
import com.bitkulcha.notification_engine.model.DeviceSetupStatusDto;
import com.bitkulcha.notification_engine.model.DeviceTypeDto;
import com.bitkulcha.notification_engine.model.HouseDetailsDto;
import com.bitkulcha.notification_engine.model.HouseMemberDto;
import com.bitkulcha.notification_engine.model.UpdateDeviceRequestDto;
import com.bitkulcha.notification_engine.service.HomeQService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeQControllerTest {

    @Mock
    private HomeQService homeQService;

    @InjectMocks
    private HomeQController homeQController;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyHouses_mapsCurrentUsersHousesToResponse() {
        UUID userId = authenticate();
        UserModel alice = user("Alice");
        UserModel charlie = user("Charlie");
        DeviceModel garage = device("Garage", DeviceTypeEnum.GARAGE_DOOR, "closed", DeviceSetupStatusEnum.ACTIVE);
        HouseModel house = HouseModelImmtbl.builder()
                .id(UUID.randomUUID())
                .name("Greenwood Manor")
                .addOwners(alice)
                .addResidents(charlie)
                .addDevices(garage)
                .build();
        when(homeQService.getHousesForMember(userId)).thenReturn(List.of(house));

        ResponseEntity<List<HouseDetailsDto>> response = homeQController.getMyHouses();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(details -> {
            assertThat(details.getId()).isEqualTo(house.getId().toString());
            assertThat(details.getName()).isEqualTo("Greenwood Manor");
            assertThat(details.getOwners()).singleElement().satisfies(owner -> {
                assertThat(owner.getId()).isEqualTo(alice.getId().toString());
                assertThat(owner.getName()).isEqualTo("Alice");
                assertThat(owner.getSurname()).isEqualTo("Smith");
                assertThat(owner.getEmail()).isEqualTo("alice@example.com");
            });
            assertThat(details.getResidents()).extracting(HouseMemberDto::getName).containsExactly("Charlie");
            assertThat(details.getDevices()).singleElement().satisfies(device -> {
                assertThat(device.getId()).isEqualTo(garage.getId().toString());
                assertThat(device.getType()).isEqualTo(DeviceTypeDto.GARAGE_DOOR);
                assertThat(device.getStatus()).isEqualTo("closed");
                assertThat(device.getSetupStatus()).isEqualTo(DeviceSetupStatusDto.ACTIVE);
            });
        });
    }

    @Test
    void createHouse_createsForCurrentUser_andReturnsCreated() {
        UUID userId = authenticate();
        HouseModel house = HouseModelImmtbl.builder().id(UUID.randomUUID()).name("Greenwood Manor").build();
        when(homeQService.createHouse(userId, "Greenwood Manor")).thenReturn(house);

        ResponseEntity<HouseDetailsDto> response = homeQController.createHouse(new CreateHouseRequestDto("Greenwood Manor"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(house.getId().toString());
        assertThat(response.getBody().getName()).isEqualTo("Greenwood Manor");
    }

    @Test
    void addDevice_passesRequestFieldsToService_andReturnsCreated() {
        UUID userId = authenticate();
        UUID houseId = UUID.randomUUID();
        DeviceModel light = device("Porch light", DeviceTypeEnum.LIGHT, null, DeviceSetupStatusEnum.ADDED);
        when(homeQService.addDevice(userId, houseId, null, "Porch light", DeviceTypeEnum.LIGHT, null, null)).thenReturn(light);

        ResponseEntity<DeviceDto> response = homeQController.addDevice(houseId,
                new CreateDeviceRequestDto("Porch light", DeviceTypeDto.LIGHT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isEqualTo(light.getId().toString());
        assertThat(response.getBody().getType()).isEqualTo(DeviceTypeDto.LIGHT);
        assertThat(response.getBody().getStatus()).isNull();
        assertThat(response.getBody().getSetupStatus()).isEqualTo(DeviceSetupStatusDto.ADDED);
    }

    @Test
    void addDevice_passesSetupStatusToService() {
        UUID userId = authenticate();
        UUID houseId = UUID.randomUUID();
        DeviceModel light = device("Porch light", DeviceTypeEnum.LIGHT, null, DeviceSetupStatusEnum.ACTIVE);
        when(homeQService.addDevice(userId, houseId, null, "Porch light", DeviceTypeEnum.LIGHT, null,
                DeviceSetupStatusEnum.ACTIVE)).thenReturn(light);

        ResponseEntity<DeviceDto> response = homeQController.addDevice(houseId,
                new CreateDeviceRequestDto("Porch light", DeviceTypeDto.LIGHT).setupStatus(DeviceSetupStatusDto.ACTIVE));

        assertThat(response.getBody().getSetupStatus()).isEqualTo(DeviceSetupStatusDto.ACTIVE);
    }

    @Test
    void addDevice_passesIdToService() {
        UUID userId = authenticate();
        UUID houseId = UUID.randomUUID();
        DeviceModel light = device("Porch light", DeviceTypeEnum.LIGHT, null, DeviceSetupStatusEnum.ACTIVE);
        when(homeQService.addDevice(userId, houseId, light.getId(), "Porch light", DeviceTypeEnum.LIGHT, null,
                DeviceSetupStatusEnum.ACTIVE)).thenReturn(light);

        ResponseEntity<DeviceDto> response = homeQController.addDevice(houseId,
                new CreateDeviceRequestDto("Porch light", DeviceTypeDto.LIGHT).id(light.getId())
                        .setupStatus(DeviceSetupStatusDto.ACTIVE));

        assertThat(response.getBody().getId()).isEqualTo(light.getId().toString());
    }

    @Test
    void updateDevice_passesRequestFieldsToService_andReturnsOk() {
        UUID userId = authenticate();
        UUID houseId = UUID.randomUUID();
        DeviceModel garage = device("Garage", DeviceTypeEnum.GARAGE_DOOR, "closed", DeviceSetupStatusEnum.ACTIVE);
        when(homeQService.updateDevice(userId, houseId, garage.getId(), "Garage", DeviceTypeEnum.GARAGE_DOOR,
                DeviceSetupStatusEnum.ACTIVE)).thenReturn(garage);

        ResponseEntity<DeviceDto> response = homeQController.updateDevice(houseId, garage.getId(),
                new UpdateDeviceRequestDto("Garage", DeviceTypeDto.GARAGE_DOOR, DeviceSetupStatusDto.ACTIVE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(garage.getId().toString());
        assertThat(response.getBody().getSetupStatus()).isEqualTo(DeviceSetupStatusDto.ACTIVE);
        assertThat(response.getBody().getStatus()).isEqualTo("closed");
    }

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        return userId;
    }

    private UserModel user(String name) {
        return UserModelImmtbl.builder()
                .id(UUID.randomUUID())
                .name(name)
                .surname("Smith")
                .email(name.toLowerCase() + "@example.com")
                .build();
    }

    private DeviceModel device(String name, DeviceTypeEnum type, String status, DeviceSetupStatusEnum setupStatus) {
        return DeviceModelImmtbl.builder()
                .id(UUID.randomUUID())
                .name(name)
                .type(type)
                .status(Optional.ofNullable(status))
                .setupStatus(setupStatus)
                .build();
    }
}
