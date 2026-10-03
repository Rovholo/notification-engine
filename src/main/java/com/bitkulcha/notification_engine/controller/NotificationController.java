package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.NotificationApi;
import com.bitkulcha.notification_engine.domain.model.EmailMessageModel;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.model.NotificationRequestDto;
import com.bitkulcha.notification_engine.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class NotificationController implements NotificationApi {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResponseEntity<NotificationRequestDto> sendMessage(NotificationRequestDto request) {
        switch (request.getType()) {
            case PUSH -> notificationService.sendPushMessage(
                    objectMapper.convertValue(request.getMessage(), PushMessageModel.class));
            case EMAIL -> notificationService.sendEmailMessage(
                    objectMapper.convertValue(request.getMessage(), EmailMessageModel.class));
            // Sending MQTT notifications isn't implemented yet; the request is accepted and nothing is sent.
            case MQTT -> log.debug("No sender for notification type {}", request.getType());
        }
        return ResponseEntity.ok().body(request);
    }
}
