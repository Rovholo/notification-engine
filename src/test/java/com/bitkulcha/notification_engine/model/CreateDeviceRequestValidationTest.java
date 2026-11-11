package com.bitkulcha.notification_engine.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateDeviceRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean accepts(String boardId) {
        return validator.validate(new CreateDeviceRequestDto("Garage", DeviceTypeDto.LIGHT).boardId(boardId)).isEmpty();
    }

    @Test
    void boardId_acceptsMacAddressesAndShellyIds() {
        assertThat(accepts("a1b2c3d4e5f6")).isTrue();
        assertThat(accepts("shelly:shelly1lg3-aabbccddeeff")).isTrue();
        assertThat(accepts("shelly:shellyplus2pm-aabbccddeeff")).isTrue();
        assertThat(accepts(null)).isTrue();
    }

    @Test
    void boardId_rejectsAnythingElse() {
        assertThat(accepts("A1B2C3D4E5F6")).isFalse();
        assertThat(accepts("shelly:")).isFalse();
        assertThat(accepts("shelly:Bad Id")).isFalse();
        assertThat(accepts("hello")).isFalse();
    }
}
