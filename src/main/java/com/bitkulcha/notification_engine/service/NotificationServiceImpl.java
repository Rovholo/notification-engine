package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.MqttMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final MessageChannel mqttOutboundChannel;
    private final FirebaseService firebaseService;

    public NotificationServiceImpl(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            FirebaseService firebaseService) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.firebaseService = firebaseService;
    }

    @Override
    public void sendPushMessage(PushMessageModel pushMessage) {
        log.debug("Sending push message: {}", pushMessage);
        firebaseService.sendMessage(pushMessage);
        log.debug("Message sent");
    }

    private void sendMqttMessage(MqttMessageModel mqttMessage) {
        log.debug("Sending MQTT message: {}", mqttMessage.toString());
        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(mqttMessage.getPayload())
                    .setHeader("mqtt_topic", mqttMessage.getTopic())
                    .build());
            firebaseService.getAllBrokers();
            log.debug(" MQTT message sent to {}", mqttMessage.getTopic());
        } catch (Exception e) {
            log.error("Error while sending MQTT message", e);
            throw new RuntimeException(e);
        }
    }

}
