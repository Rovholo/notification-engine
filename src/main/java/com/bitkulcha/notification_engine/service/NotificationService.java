package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.model.NotificationRequest;

public interface NotificationService {

    NotificationRequest sendMessage(NotificationRequest request);
}
