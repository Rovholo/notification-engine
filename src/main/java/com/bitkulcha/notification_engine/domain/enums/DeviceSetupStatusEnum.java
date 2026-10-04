package com.bitkulcha.notification_engine.domain.enums;

/**
 * Where a device is in its setup lifecycle. Separate from a device's live status (e.g. "closed").
 * The app only talks to ACTIVE devices over MQTT.
 */
public enum DeviceSetupStatusEnum {
    // Saved on the server, but the device hasn't received its wifi/broker details yet.
    ADDED,
    ACTIVE,
    INACTIVE
}
