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
}
