package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.DeviceModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.HouseModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.domain.model.UserModelImmtbl;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceMessageServiceTest {

    private final UUID houseId = UuidCreator.getTimeOrderedEpoch();
    private final UUID deviceId = UuidCreator.getTimeOrderedEpoch();
    private final UUID ownerId = UuidCreator.getTimeOrderedEpoch();
    private final String reactionTopic = "home/" + houseId + "/reaction/" + deviceId;
    private final String willTopic = "home/" + houseId + "/will/" + deviceId;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private HomeQService homeQService;

    @InjectMocks
    private DeviceMessageService deviceMessageService;

    @Test
    void handleMessage_startReaction_activatesTheDeviceAndNotifiesItIsOnline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(reactionTopic, "{\"index\":0,\"status\":\"closed\",\"mode\":\"start\"}");

        verify(homeQService).activateAddedDevice(houseId, deviceId);
        verifyPushed("Your Gate is online");
    }

    @Test
    void handleMessage_statusChange_notifiesTheOwnersWithTheNameFromTheDatabase() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(reactionTopic,
                "{\"index\":0,\"status\":\"open\",\"prevStatus\":\"closed\"}");

        verifyPushed("Your Gate is open");
        verify(homeQService, never()).activateAddedDevice(any(), any());
    }

    @Test
    void handleMessage_statusWithoutPrevStatus_isIgnored() {
        deviceMessageService.handleMessage(reactionTopic, "{\"index\":0,\"status\":\"closed\",\"old\":\"open\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
        verify(homeQService, never()).activateAddedDevice(any(), any());
    }

    @Test
    void handleMessage_will_notifiesTheOwnersTheDeviceIsOffline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(willTopic, "{\"status\":\"offline\"}");

        verifyPushed("Your Gate is offline");
    }

    @Test
    void handleMessage_onlineOnWill_notifiesTheOwnersTheDeviceIsOnline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(willTopic, "{\"status\":\"online\"}");

        verifyPushed("Your Gate is online");
        verify(homeQService, never()).activateAddedDevice(any(), any());
    }

    @Test
    void handleMessage_unknownDevice_doesNotNotify() {
        when(homeQService.findHouse(houseId)).thenReturn(Optional.of(house(List.of())));

        deviceMessageService.handleMessage(willTopic, "{\"status\":\"offline\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_whenNotificationFails_stillActivatesTheDevice() {
        givenDeviceNamed("Gate");
        doThrow(new RuntimeException("firebase down")).when(firebaseService).sendMessage(anyList(), any());

        deviceMessageService.handleMessage(reactionTopic, "{\"status\":\"closed\",\"mode\":\"start\"}");

        verify(homeQService).activateAddedDevice(houseId, deviceId);
    }

    @Test
    void handleMessage_actionTopic_isIgnored() {
        // Actions are what the app sends, so only the device's own messages count.
        deviceMessageService.handleMessage("home/" + houseId + "/action/" + deviceId,
                "{\"mode\":\"start\",\"prevStatus\":\"open\"}");

        verify(homeQService, never()).activateAddedDevice(any(), any());
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_badTopicOrPayload_isIgnored() {
        deviceMessageService.handleMessage("home/not-a-uuid/reaction/" + deviceId, "{\"mode\":\"start\"}");
        deviceMessageService.handleMessage(reactionTopic, "not json");
        deviceMessageService.handleMessage("topic/test", "{\"mode\":\"start\"}");
        deviceMessageService.handleMessage(null, "{\"mode\":\"start\"}");

        verify(homeQService, never()).activateAddedDevice(any(), any());
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    private void givenDeviceNamed(String name) {
        when(homeQService.findHouse(houseId)).thenReturn(Optional.of(house(List.of(DeviceModelImmtbl.builder()
                .id(deviceId)
                .name(name)
                .type(DeviceTypeEnum.GARAGE_DOOR)
                .setupStatus(DeviceSetupStatusEnum.ACTIVE)
                .build()))));
    }

    private HouseModel house(List<DeviceModel> devices) {
        return HouseModelImmtbl.builder()
                .id(houseId)
                .name("Greenwood Manor")
                .addOwners(UserModelImmtbl.builder().id(ownerId).name("Alice").surname("Smith").email("a@b.c").build())
                .devices(devices)
                .build();
    }

    private void verifyPushed(String body) {
        ArgumentCaptor<PushMessageModel> push = ArgumentCaptor.forClass(PushMessageModel.class);
        verify(firebaseService).sendMessage(eq(List.of(ownerId.toString())), push.capture());
        assertThat(push.getValue().getNotification().getTitle()).isEqualTo("Greenwood Manor");
        assertThat(push.getValue().getNotification().getBody()).isEqualTo(body);
    }
}
