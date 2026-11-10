package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.enums.StatusChangeEnum;
import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.exception.DeviceIdConflictException;
import com.bitkulcha.notification_engine.exception.DeviceNotFoundException;
import com.bitkulcha.notification_engine.exception.HouseAccessDeniedException;
import com.bitkulcha.notification_engine.exception.HouseMemberConflictException;
import com.bitkulcha.notification_engine.exception.HouseNotFoundException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
import com.bitkulcha.notification_engine.exception.InvalidDeviceIdException;
import com.bitkulcha.notification_engine.exception.UserNotFoundException;
import com.bitkulcha.notification_engine.repository.DeviceRepository;
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
    final DeviceRepository deviceRepository;

    static final String OFFLINE = "offline";

    public HomeQService(HouseRepository houseRepository, UserRepository userRepository,
                        DeviceRepository deviceRepository) {
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
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

    @Transactional
    public DeviceModel addDevice(UUID userId, UUID houseId, UUID deviceId, String name, DeviceTypeEnum type,
                                 String status, DeviceSetupStatusEnum setupStatus, String secret,
                                 UUID hardwareId, Integer channel, String boardId) {
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
        // A retry without a secret, e.g. a device saved while offline, keeps the one the device was set up with.
        if (secret != null) device.setSecret(secret);
        device.setHardwareId(hardwareId);
        device.setChannel(channel);
        if (boardId != null) device.setBoardId(boardId);
        // New devices haven't been sent their wifi/broker details yet unless the caller says otherwise.
        device.setSetupStatus(setupStatus != null ? setupStatus : DeviceSetupStatusEnum.ADDED);
        return EntityModelMapper.toModel(device);
    }

    @Transactional
    public DeviceModel claimDeviceSecret(UUID userId, UUID houseId, UUID deviceId, String secret) {
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can secure devices in a house");
        DeviceEntity device = findDevice(house, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("No such device in this house"));

        if (device.getSecret() == null) device.setSecret(secret);
        return EntityModelMapper.toModel(device);
    }

    @Transactional
    public DeviceModel updateDevice(UUID userId, UUID houseId, UUID deviceId, String name, DeviceTypeEnum type,
                                    DeviceSetupStatusEnum setupStatus) {
        return updateDevice(userId, houseId, deviceId, name, type, setupStatus, null, null);
    }

    @Transactional
    public DeviceModel updateDevice(UUID userId, UUID houseId, UUID deviceId, String name, DeviceTypeEnum type,
                                    DeviceSetupStatusEnum setupStatus, Boolean notifyStatus,
                                    Boolean notifyConnection) {
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can update devices in a house");
        DeviceEntity device = findDevice(house, deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("No such device in this house"));

        device.setName(name.strip());
        device.setType(type);
        device.setSetupStatus(setupStatus);
        if (notifyStatus != null) device.setNotifyStatus(notifyStatus);
        if (notifyConnection != null) device.setNotifyConnection(notifyConnection);
        return EntityModelMapper.toModel(device);
    }

    /**
     * Stores the status a device reported and says whether it is a change from the last one stored. A device that
     * had no status yet, or was only known as offline, gets its first status, which is not a change.
     */
    @Transactional
    public StatusChangeEnum recordStatus(UUID houseId, UUID deviceId, String status) {
        Optional<DeviceEntity> device = deviceRepository.findInHouse(houseId, deviceId);
        if (device.isEmpty()) return StatusChangeEnum.UNKNOWN_DEVICE;
        String previous = device.get().getStatus();
        if (status.equals(previous)) return StatusChangeEnum.UNCHANGED;
        int updated = previous == null
                ? deviceRepository.setFirstStatus(houseId, deviceId, status)
                : deviceRepository.replaceStatus(houseId, deviceId, previous, status);
        if (updated == 0) return StatusChangeEnum.UNCHANGED;
        return previous == null || OFFLINE.equals(previous) ? StatusChangeEnum.FIRST : StatusChangeEnum.CHANGED;
    }

    @Transactional
    public UserModel addResident(UUID userId, UUID houseId, String email) {
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can add residents to a house");
        UserEntity resident = findUserByEmail(email.strip())
                .orElseThrow(() -> new UserNotFoundException("No user has that email"));
        if (isMember(house, resident.getId())) {
            throw new HouseMemberConflictException("That user already belongs to this house");
        }
        house.getResidents().add(resident);
        return EntityModelMapper.toModel(resident);
    }

    @Transactional
    public void removeResident(UUID userId, UUID houseId, UUID residentId) {
        HouseEntity house = findHouseAsOwner(userId, houseId, "Only owners can remove residents from a house");
        if (!house.getResidents().removeIf(resident -> resident.getId().equals(residentId))) {
            throw new UserNotFoundException("No such resident in this house");
        }
    }

    @Transactional
    public boolean activateAddedDevice(UUID houseId, UUID deviceId) {
        return houseRepository.findById(houseId)
                .flatMap(house -> findDevice(house, deviceId))
                .filter(device -> device.getSetupStatus() == DeviceSetupStatusEnum.ADDED)
                .map(device -> {
                    device.setSetupStatus(DeviceSetupStatusEnum.ACTIVE);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Puts a device that was reset with its button back to waiting for setup, so the app offers to set it up again.
     * Its stored status goes back to offline, so the status it reports after setup is a first status, not a change.
     */
    @Transactional
    public boolean markDeviceReset(UUID houseId, UUID deviceId) {
        return deviceRepository.findInHouse(houseId, deviceId)
                .filter(device -> device.getSetupStatus() == DeviceSetupStatusEnum.ACTIVE)
                .map(device -> {
                    device.setSetupStatus(DeviceSetupStatusEnum.ADDED);
                    device.setStatus(OFFLINE);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<HouseModel> findHouse(UUID houseId) {
        return houseRepository.findById(houseId).map(EntityModelMapper::toModel);
    }

    private static Optional<DeviceEntity> findDevice(HouseEntity house, UUID deviceId) {
        return house.getDevices().stream().filter(d -> d.getId().equals(deviceId)).findFirst();
    }

    // Emails are stored as typed, so an exact match wins and otherwise a single case-insensitive match is used.
    private Optional<UserEntity> findUserByEmail(String email) {
        Optional<UserEntity> exact = userRepository.findByEmail(email);
        if (exact.isPresent()) return exact;
        List<UserEntity> matches = userRepository.findAllByEmailIgnoreCase(email);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
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
