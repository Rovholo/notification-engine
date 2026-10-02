package com.bitkulcha.notification_engine.controller;


import com.bitkulcha.notification_engine.api.HomeqApi;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.model.CreateDeviceRequestDto;
import com.bitkulcha.notification_engine.model.CreateHouseRequestDto;
import com.bitkulcha.notification_engine.model.DeviceDto;
import com.bitkulcha.notification_engine.model.DeviceTypeDto;
import com.bitkulcha.notification_engine.model.HouseDetailsDto;
import com.bitkulcha.notification_engine.model.HouseMemberDto;
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

    public HomeQController(HomeQService homeQService) {
        this.homeQService = homeQService;
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
        DeviceModel device = homeQService.addDevice(currentUserId(), houseId, createDeviceRequest.getName(),
                DeviceTypeEnum.valueOf(createDeviceRequest.getType().name()), createDeviceRequest.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDevice(device));
    }

    // JwtAuthenticationFilter sets the user id as the principal.
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
        return new DeviceDto(device.getId().toString(), device.getName(), DeviceTypeDto.fromValue(device.getType().name()))
                .status(device.getStatus().orElse(null));
    }
}
