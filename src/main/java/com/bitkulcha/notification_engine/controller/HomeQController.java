package com.bitkulcha.notification_engine.controller;


import com.bitkulcha.notification_engine.api.HomeqApi;
import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.model.AddResidentRequestDto;
import com.bitkulcha.notification_engine.model.ClaimDeviceSecretRequestDto;
import com.bitkulcha.notification_engine.model.CreateDeviceRequestDto;
import com.bitkulcha.notification_engine.model.CreateHouseRequestDto;
import com.bitkulcha.notification_engine.model.DeviceDto;
import com.bitkulcha.notification_engine.model.DeviceSetupStatusDto;
import com.bitkulcha.notification_engine.model.DeviceTypeDto;
import com.bitkulcha.notification_engine.model.HouseDetailsDto;
import com.bitkulcha.notification_engine.model.HouseMemberDto;
import com.bitkulcha.notification_engine.model.UpdateDeviceRequestDto;
import com.bitkulcha.notification_engine.service.DeviceOwnerService;
import com.bitkulcha.notification_engine.service.HomeQService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class HomeQController implements HomeqApi {

    final HomeQService homeQService;
    final DeviceOwnerService deviceOwnerService;

    public HomeQController(HomeQService homeQService, DeviceOwnerService deviceOwnerService) {
        this.homeQService = homeQService;
        this.deviceOwnerService = deviceOwnerService;
    }

    @Override
    public ResponseEntity<List<HouseDetailsDto>> getMyHouses() {
        List<HouseDetailsDto> houses = homeQService.getHousesForMember(currentUserId()).stream()
                .map(HomeQController::toHouseDetails)
                .toList();
        return ResponseEntity.ok(houses);
    }

    @Override
    public ResponseEntity<HouseDetailsDto> createHouse(CreateHouseRequestDto createHouseRequest) {
        HouseModel house = homeQService.createHouse(currentUserId(), createHouseRequest.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toHouseDetails(house));
    }

    @Override
    public ResponseEntity<DeviceDto> addDevice(UUID houseId, CreateDeviceRequestDto createDeviceRequest) {
        DeviceSetupStatusDto setupStatus = createDeviceRequest.getSetupStatus();
        DeviceModel device = homeQService.addDevice(currentUserId(), houseId, createDeviceRequest.getId(),
                createDeviceRequest.getName(),
                DeviceTypeEnum.valueOf(createDeviceRequest.getType().name()), createDeviceRequest.getStatus(),
                setupStatus == null ? null : DeviceSetupStatusEnum.valueOf(setupStatus.name()),
                createDeviceRequest.getSecret(), createDeviceRequest.getHardwareId(), createDeviceRequest.getChannel(),
                createDeviceRequest.getBoardId());
        if (createDeviceRequest.getBoardId() != null) deviceOwnerService.publishOwner(houseId, device);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDevice(device));
    }

    @Override
    public ResponseEntity<DeviceDto> updateDevice(UUID houseId, UUID deviceId, UpdateDeviceRequestDto updateDeviceRequest) {
        DeviceModel device = homeQService.updateDevice(currentUserId(), houseId, deviceId, updateDeviceRequest.getName(),
                DeviceTypeEnum.valueOf(updateDeviceRequest.getType().name()),
                DeviceSetupStatusEnum.valueOf(updateDeviceRequest.getSetupStatus().name()),
                updateDeviceRequest.getNotifyStatus(), updateDeviceRequest.getNotifyConnection());
        return ResponseEntity.ok(toDevice(device));
    }

    @Override
    public ResponseEntity<DeviceDto> claimDeviceSecret(UUID houseId, UUID deviceId,
                                                       ClaimDeviceSecretRequestDto claimDeviceSecretRequest) {
        DeviceModel device = homeQService.claimDeviceSecret(currentUserId(), houseId, deviceId,
                claimDeviceSecretRequest.getSecret());
        return ResponseEntity.ok(toDevice(device));
    }

    @Override
    public ResponseEntity<HouseMemberDto> addResident(UUID houseId, AddResidentRequestDto addResidentRequest) {
        UserModel resident = homeQService.addResident(currentUserId(), houseId, addResidentRequest.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(toHouseMember(resident));
    }

    @Override
    public ResponseEntity<Void> removeResident(UUID houseId, UUID userId) {
        homeQService.removeResident(currentUserId(), houseId, userId);
        return ResponseEntity.noContent().build();
    }

    private static UUID currentUserId() {
        return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private static HouseDetailsDto toHouseDetails(HouseModel house) {
        return new HouseDetailsDto(
                house.getId().toString(),
                house.getName(),
                house.getOwners().stream().map(HomeQController::toHouseMember).toList(),
                house.getResidents().stream().map(HomeQController::toHouseMember).toList(),
                house.getDevices().stream().map(HomeQController::toDevice).toList());
    }

    private static HouseMemberDto toHouseMember(UserModel user) {
        return new HouseMemberDto(user.getId().toString(), user.getName(), user.getSurname(), user.getEmail());
    }

    private static DeviceDto toDevice(DeviceModel device) {
        return new DeviceDto(device.getId().toString(), device.getName(), DeviceTypeDto.fromValue(device.getType().name()),
                DeviceSetupStatusDto.fromValue(device.getSetupStatus().name()))
                .status(device.getStatus().orElse(null))
                .secret(device.getSecret().orElse(null))
                .hardwareId(device.getHardwareId().map(UUID::toString).orElse(null))
                .channel(device.getChannel().orElse(null))
                .boardId(device.getBoardId().orElse(null))
                .notifyStatus(device.notifiesStatus())
                .notifyConnection(device.notifiesConnection());
    }
}
