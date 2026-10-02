package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.mapper.EntityModelMapper;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.exception.HouseAccessDeniedException;
import com.bitkulcha.notification_engine.exception.HouseNotFoundException;
import com.bitkulcha.notification_engine.exception.InvalidCredentialsException;
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
import java.util.UUID;

@Service
public class HomeQServiceImpl implements HomeQService {

    final HouseRepository houseRepository;
    final UserRepository userRepository;

    public HomeQServiceImpl(HouseRepository houseRepository, UserRepository userRepository) {
        this.houseRepository = houseRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HouseModel> getHousesForMember(UUID userId) {
        return houseRepository.findAllByMember(userId).stream()
                .sorted(Comparator.comparing(HouseEntity::getName))
                .map(EntityModelMapper::toModel)
                .toList();
    }

    @Override
    @Transactional
    public HouseModel createHouse(UUID ownerId, String name) {
        UserEntity owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));

        HouseEntity house = new HouseEntity();
        house.setName(name.strip());
        house.getOwners().add(owner);
        return EntityModelMapper.toModel(houseRepository.save(house));
    }

    @Override
    @Transactional
    public DeviceModel addDevice(UUID userId, UUID houseId, String name, DeviceTypeEnum type, String status) {
        HouseEntity house = houseRepository.findById(houseId)
                .filter(h -> isMember(h, userId))
                // Non-members get the same 404 as a missing house so house ids can't be probed.
                .orElseThrow(() -> new HouseNotFoundException("No such house"));
        if (!containsUser(house.getOwners(), userId)) {
            throw new HouseAccessDeniedException("Only owners can add devices to a house");
        }

        DeviceEntity device = new DeviceEntity();
        device.setHouse(house);
        device.setName(name.strip());
        device.setType(type);
        device.setStatus(status);
        // Persisted by the cascade on HouseEntity.devices. Calling save() would merge a copy, because the id is
        // pre-assigned, and the copy would clash with this instance when the cascade runs.
        house.getDevices().add(device);
        return EntityModelMapper.toModel(device);
    }

    private static boolean isMember(HouseEntity house, UUID userId) {
        return containsUser(house.getOwners(), userId) || containsUser(house.getResidents(), userId);
    }

    private static boolean containsUser(Collection<UserEntity> users, UUID userId) {
        return users.stream().anyMatch(user -> user.getId().equals(userId));
    }
}
