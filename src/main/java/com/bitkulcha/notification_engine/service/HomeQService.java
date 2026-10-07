package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HomeQService {

    final HouseRepository houseRepository;
    final UserRepository userRepository;

    public HomeQService(HouseRepository houseRepository, UserRepository userRepository) {
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<HouseModel> getHousesForMember(UUID userId) {
        return houseRepository.findAllByMember(userId).stream()
                .sorted(Comparator.comparing(HouseEntity::getName))
                .map(EntityModelMapper::toModel)
                .toList();
    }

    @Transactional
    public HouseModel createHouse(UUID ownerId, String name) {
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

        HouseEntity house = new HouseEntity();
        house.setName(name.strip());
        house.getOwners().add(owner);
        return EntityModelMapper.toModel(houseRepository.save(house));
    }

    /**
     * The app may pick {@code deviceId} itself, so it can send it to the device while offline on the device's access
     * point. The same id can then arrive again on a retry, so a device that already has it in this house is updated
     * and returned rather than added twice.
     */
    @Transactional
    public DeviceModel addDevice(UUID userId, UUID houseId, UUID deviceId, String name, DeviceTypeEnum type,
                                 String status, DeviceSetupStatusEnum setupStatus) {
        // Time ordered ids keep inserts at the end of the primary key index, like the ones the server makes.
        if (deviceId != null && deviceId.version() != 7) {
            throw new InvalidDeviceIdException("Device id must be a UUIDv7");
        }
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can add devices to a house");

        DeviceEntity device = deviceId == null ? null : findDevice(house, deviceId).orElse(null);
        if (device == null) {
            if (deviceId != null && houseRepository.existsDeviceById(deviceId)) {
                throw new DeviceIdConflictException("Device id is already in use");
            }
            device = new DeviceEntity();
            if (deviceId != null) device.setId(deviceId);
            device.setHouse(house);
            // Persisted by the cascade on HouseEntity.devices. Calling save() would merge a copy, because the id is
            // pre-assigned, and the copy would clash with this instance when the cascade runs.
            house.getDevices().add(device);
        }
        device.setName(name.strip());
        device.setType(type);
        device.setStatus(status);
        // New devices haven't been sent their wifi/broker details yet unless the caller says otherwise.
        device.setSetupStatus(setupStatus != null ? setupStatus : DeviceSetupStatusEnum.ADDED);
        return EntityModelMapper.toModel(device);
    }

    @Transactional
    public DeviceModel updateDevice(UUID userId, UUID houseId, UUID deviceId, String name, DeviceTypeEnum type,
                                    DeviceSetupStatusEnum setupStatus) {
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can update devices in a house");
        DeviceEntity device = findDevice(house, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("No such device in this house"));

        device.setName(name.strip());
        device.setType(type);
        device.setSetupStatus(setupStatus);
        return EntityModelMapper.toModel(device);
    }

    private static Optional<DeviceEntity> findDevice(HouseEntity house, UUID deviceId) {
        return house.getDevices().stream().filter(d -> d.getId().equals(deviceId)).findFirst();
    }

    private HouseEntity findHouseAsOwner(UUID userId, UUID houseId, String notOwnerMessage) {
        HouseEntity house = houseRepository.findById(houseId)
                .filter(h -> isMember(h, userId))
                // Non-members get the same 404 as a missing house so house ids can't be probed.
                .orElseThrow(() -> new HouseNotFoundException("No such house"));
        if (!containsUser(house.getOwners(), userId)) {
            throw new HouseAccessDeniedException(notOwnerMessage);
        }
        return house;
    }

    private static boolean isMember(HouseEntity house, UUID userId) {
        return containsUser(house.getOwners(), userId) || containsUser(house.getResidents(), userId);
    }

    private static boolean containsUser(Collection<UserEntity> users, UUID userId) {
        return users.stream().anyMatch(user -> user.getId().equals(userId));
    }
}
