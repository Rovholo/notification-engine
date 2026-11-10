package com.bitkulcha.notification_engine.domain.model;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import org.immutables.value.Value;

import java.util.Optional;
import java.util.UUID;

@Value.Immutable
public interface DeviceModel {
    UUID getId();
    String getName();
    DeviceTypeEnum getType();
    Optional<String> getStatus();
    DeviceSetupStatusEnum getSetupStatus();
    Optional<String> getSecret();
    Optional<UUID> getHardwareId();
    Optional<Integer> getChannel();
    Optional<String> getBoardId();
    Optional<Boolean> getNotifyStatus();
    Optional<Boolean> getNotifyConnection();

    default boolean notifiesStatus() {
        return getNotifyStatus().orElse(getType().notifiesByDefault());
    }

    default boolean notifiesConnection() {
        return getNotifyConnection().orElse(getType().notifiesByDefault());
    }
}
