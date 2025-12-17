package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.api.NotificationApi;
import com.bitkulcha.notification_engine.model.NotificationRequest;
import com.bitkulcha.notification_engine.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController implements NotificationApi {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public ResponseEntity<NotificationRequest> sendMessage(NotificationRequest request) {
        return ResponseEntity.ok().body(notificationService.sendMessage(request));
    }
}
