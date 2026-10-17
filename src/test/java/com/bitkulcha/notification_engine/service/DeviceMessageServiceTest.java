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
    private final String stateTopic = "home/" + houseId + "/state/" + deviceId;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private HomeQService homeQService;

    @InjectMocks
    private DeviceMessageService deviceMessageService;

    @Test
    void handleMessage_connectState_activatesTheDeviceAndNotifiesItIsOnline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(stateTopic, "{\"index\":0,\"status\":\"closed\",\"mode\":\"connect\"}");

        verify(homeQService).activateAddedDevice(houseId, deviceId);
        verifyPushed("Your Gate is online", deviceId + "-connection");
    }

    @Test
    void handleMessage_statusChange_notifiesTheOwnersWithTheNameFromTheDatabase() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(stateTopic,
                "{\"index\":0,\"status\":\"open\",\"prevStatus\":\"closed\"}");

        verifyPushed("Your Gate is open", deviceId + "-status");
        verify(homeQService, never()).activateAddedDevice(any(), any());
    }

    @Test
    void handleMessage_stateWithoutPrevStatus_isIgnored() {
        deviceMessageService.handleMessage(stateTopic, "{\"index\":0,\"status\":\"closed\",\"old\":\"open\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
        verify(homeQService, never()).activateAddedDevice(any(), any());
    }

    @Test
    void handleMessage_actionReply_isIgnored() {
        deviceMessageService.handleMessage(reactionTopic,
                "{\"id\":\"0\",\"action\":\"operate\",\"index\":0,\"success\":true,\"status\":\"open\"}");

        verify(homeQService, never()).activateAddedDevice(any(), any());
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_legacyStatusChangeReaction_notifiesTheOwners() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(reactionTopic,
                "{\"index\":0,\"status\":\"open\",\"prevStatus\":\"closed\"}");

        verifyPushed("Your Gate is open", deviceId + "-status");
    }

    @Test
    void handleMessage_legacyStartReaction_activatesTheDeviceAndNotifiesItIsOnline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(reactionTopic, "{\"index\":0,\"status\":\"closed\",\"mode\":\"start\"}");

        verify(homeQService).activateAddedDevice(houseId, deviceId);
        verifyPushed("Your Gate is online", deviceId + "-connection");
    }

    @Test
    void handleMessage_legacyWill_notifiesTheOwnersTheDeviceIsOffline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage("home/" + houseId + "/will/" + deviceId, "{\"status\":\"offline\"}");

        verifyPushed("Your Gate is offline", deviceId + "-connection");
    }

    @Test
    void handleMessage_legacyOnlineWill_notifiesTheOwnersTheDeviceIsOnline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage("home/" + houseId + "/will/" + deviceId, "{\"status\":\"online\"}");

        verifyPushed("Your Gate is online", deviceId + "-connection");
    }

    @Test
    void handleMessage_offlineState_notifiesTheOwnersTheDeviceIsOffline() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");

        verifyPushed("Your Gate is offline", deviceId + "-connection");
    }

    @Test
    void handleMessage_whenNotificationFails_stillActivatesTheDevice() {
        givenDeviceNamed("Gate");
        doThrow(new RuntimeException("firebase down")).when(firebaseService).sendMessage(anyList(), any());

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"closed\",\"mode\":\"connect\"}");

        verify(homeQService).activateAddedDevice(houseId, deviceId);
    }

    @Test
    void handleMessage_unknownDevice_doesNotNotify() {
        when(homeQService.findHouse(houseId)).thenReturn(Optional.of(house(List.of())));

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_actionTopic_isIgnored() {
        // Actions are what the app sends, so only the device's own messages count.
        deviceMessageService.handleMessage("home/" + houseId + "/action/" + deviceId,
                "{\"mode\":\"connect\",\"prevStatus\":\"open\"}");

        verify(homeQService, never()).activateAddedDevice(any(), any());
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_badTopicOrPayload_isIgnored() {
        deviceMessageService.handleMessage("home/not-a-uuid/state/" + deviceId, "{\"mode\":\"connect\"}");
        deviceMessageService.handleMessage(stateTopic, "not json");
        deviceMessageService.handleMessage("topic/test", "{\"mode\":\"connect\"}");
        deviceMessageService.handleMessage(null, "{\"mode\":\"connect\"}");

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

    private void verifyPushed(String body, String collapseKey) {
        ArgumentCaptor<PushMessageModel> push = ArgumentCaptor.forClass(PushMessageModel.class);
        verify(firebaseService).sendMessage(eq(List.of(ownerId.toString())), push.capture());
        assertThat(push.getValue().getNotification().getTitle()).isEqualTo("Greenwood Manor");
        assertThat(push.getValue().getNotification().getBody()).isEqualTo(body);
        assertThat(push.getValue().getCollapseKey()).contains(collapseKey);
    }
}
