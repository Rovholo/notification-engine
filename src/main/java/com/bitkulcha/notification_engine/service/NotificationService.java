package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.PushMessageModel;

public interface NotificationService {

    void sendPushMessage(PushMessageModel pushMessage);
}
