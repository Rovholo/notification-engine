package com.bitkulcha.notification_engine.domain.mapper;

import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.AccountModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.domain.model.BrokerModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.DeviceModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.HouseModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.domain.model.UserModelImmtbl;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.DeviceEntity;
import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Converts JPA entities into immutable domain models; services hand only models to the rest of the app.
public final class EntityModelMapper {

    private EntityModelMapper() {
    }

    public static UserModel toModel(UserEntity user) {
        return UserModelImmtbl.builder()
                .id(user.getId())
                .name(user.getName())
                .surname(user.getSurname())
                .email(user.getEmail())
                .cell(Optional.ofNullable(user.getCell()))
                .build();
    }

    public static AccountModel toModel(CredentialEntity credential) {
        return AccountModelImmtbl.builder()
                .username(credential.getUsername())
                .user(toModel(credential.getUser()))
                .build();
    }

    public static DeviceModel toModel(DeviceEntity device) {
        return DeviceModelImmtbl.builder()
                .id(device.getId())
                .name(device.getName())
                .type(device.getType())
                .status(Optional.ofNullable(device.getStatus()))
                .build();
    }

    // Members and devices are sorted by name so responses have a stable order.
    public static HouseModel toModel(HouseEntity house) {
        return HouseModelImmtbl.builder()
                .id(house.getId())
                .name(house.getName())
                .owners(toUserModels(house.getOwners()))
                .residents(toUserModels(house.getResidents()))
                .devices(house.getDevices().stream()
                        .sorted(Comparator.comparing(DeviceEntity::getName))
                        .map(EntityModelMapper::toModel)
                        .toList())
                .build();
    }

    public static BrokerModel toModel(BrokerEntity broker) {
        return BrokerModelImmtbl.builder()
                .id(broker.getId())
                .server(broker.getServer())
                .username(broker.getUsername())
                .password(broker.getPassword())
                .isSecure(broker.getSecure())
                .build();
    }

    private static List<UserModel> toUserModels(Collection<UserEntity> users) {
        return users.stream()
                .sorted(Comparator.comparing(UserEntity::getName).thenComparing(UserEntity::getSurname))
                .map(EntityModelMapper::toModel)
                .toList();
    }
}
