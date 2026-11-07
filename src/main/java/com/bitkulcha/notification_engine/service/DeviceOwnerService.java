package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publishes which board owns each device id as a retained message, so a board that was replaced but still runs
 * with the same id stops itself when it reads a different owner.
 */
@Slf4j
@Service
public class DeviceOwnerService {

    private final MessageChannel mqttOutboundChannel;

    public DeviceOwnerService(@Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void publishOwner(UUID houseId, DeviceModel device) {
        device.getBoardId().ifPresent(boardId -> {
            String topic = "home/" + houseId + "/owner/" + device.getId();
            try {
                mqttOutboundChannel.send(MessageBuilder
                        .withPayload("{\"board\":\"" + boardId + "\"}")
                        .setHeader(MqttHeaders.TOPIC, topic)
                        .setHeader(MqttHeaders.RETAINED, true)
                        .setHeader(MqttHeaders.QOS, 1)
                        .build());
                log.info("Published board {} as owner of device {} in house {}", boardId, device.getId(), houseId);
            } catch (Exception e) {
                log.error("Error publishing owner of device {} in house {}", device.getId(), houseId, e);
            }
        });
    }
}
