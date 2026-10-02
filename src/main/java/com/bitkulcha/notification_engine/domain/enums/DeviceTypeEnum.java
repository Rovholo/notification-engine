package com.bitkulcha.notification_engine.domain.enums;

import lombok.Getter;

@Getter
public enum DeviceTypeEnum {
    GARAGE_DOOR("garage_door", "Garage Door"),
    LIGHT("light", "Light"),
    AIR_CON("air_con", "Air Con"),
    SENSOR("sensor", "Sensor");

    private final String value;
    private final String label;

    DeviceTypeEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

}
