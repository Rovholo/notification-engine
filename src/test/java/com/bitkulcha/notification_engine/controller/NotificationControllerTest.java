package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.model.NotificationRequest;
import com.bitkulcha.notification_engine.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void sendMessage_delegatesToServiceAndReturnsItsResult() {
        NotificationRequest request = new NotificationRequest(
                NotificationRequest.TypeEnum.PUSH, Map.of("topic", "house-1"));
        when(notificationService.sendMessage(request)).thenReturn(request);

        ResponseEntity<NotificationRequest> response = notificationController.sendMessage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(request);
        verify(notificationService).sendMessage(request);
    }
}