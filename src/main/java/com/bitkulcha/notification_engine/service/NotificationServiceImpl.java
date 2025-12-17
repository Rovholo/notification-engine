package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.MqttMessageDto;
import com.bitkulcha.notification_engine.dto.PushMessageDto;
import com.bitkulcha.notification_engine.model.NotificationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(NotificationServiceImpl.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageChannel mqttOutboundChannel;
    private final FirebaseService firebaseService;

    public NotificationServiceImpl(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            FirebaseService firebaseService) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.firebaseService = firebaseService;
    }

    @Override
    public NotificationRequest sendMessage(NotificationRequest body) {
        if (body.getType().equals(NotificationRequest.TypeEnum.MQTT)) {
            sendMqttMessage(objectMapper.convertValue(body.getMessage(), MqttMessageDto.class) );
        } else if (body.getType().equals(NotificationRequest.TypeEnum.PUSH)) {
            firebaseService.sendMessage(objectMapper.convertValue(body.getMessage(), PushMessageDto.class));
        }
        return body;
    }

    private void sendMqttMessage(MqttMessageDto mqttMessage) {
        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(mqttMessage.getPayload())
                    .setHeader("mqtt_topic", mqttMessage.getTopic())
                    .build());
            firebaseService.getAllBrokers();
        } catch (Exception e) {
            log.error("Error while sending MQTT message", e);
            throw new RuntimeException(e);
        }
    }

}
