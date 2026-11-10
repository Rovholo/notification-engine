package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.DeviceSetupStatusEnum;
import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.enums.StatusChangeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.DeviceModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.HouseModelImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModel;
import com.bitkulcha.notification_engine.domain.model.UserModelImmtbl;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private final long now = 1_762_790_000L;

    @BeforeEach
    void fixClock() {
        deviceMessageService.clock = Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC);
    }

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
        givenStatusChange("open", StatusChangeEnum.CHANGED);

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
        givenStatusChange("open", StatusChangeEnum.CHANGED);

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
    void handleMessage_offlineState_notifiesEveryChannelOnTheBoard() {
        UUID secondId = UuidCreator.getTimeOrderedEpoch();
        when(homeQService.findHouse(houseId)).thenReturn(Optional.of(house(List.of(
                DeviceModelImmtbl.builder().id(deviceId).name("Gate").type(DeviceTypeEnum.GARAGE_DOOR)
                        .setupStatus(DeviceSetupStatusEnum.ACTIVE).hardwareId(deviceId).channel(0).build(),
                DeviceModelImmtbl.builder().id(secondId).name("Porch light").type(DeviceTypeEnum.LIGHT)
                        .setupStatus(DeviceSetupStatusEnum.ACTIVE).hardwareId(deviceId).channel(1)
                        .notifyConnection(true).build(),
                DeviceModelImmtbl.builder().id(UuidCreator.getTimeOrderedEpoch()).name("Pool pump")
                        .type(DeviceTypeEnum.LIGHT).setupStatus(DeviceSetupStatusEnum.ACTIVE).build()))));

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");

        ArgumentCaptor<PushMessageModel> push = ArgumentCaptor.forClass(PushMessageModel.class);
        verify(firebaseService, times(2)).sendMessage(eq(List.of(ownerId.toString())), push.capture());
        assertThat(push.getAllValues()).extracting(p -> p.getNotification().getBody())
                .containsExactly("Your Gate is offline", "Your Porch light is offline");
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

    @Test
    void handleMessage_changeWithoutPrevStatus_notifiesFromTheStoredStatus() {
        givenDeviceNamed("Gate");
        givenStatusChange("open", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"cause\":\"input\",\"ts\":" + now + "}");

        verifyPushed("Your Gate is open", deviceId + "-status");
    }

    @Test
    void handleMessage_repeatedStatus_doesNotNotifyAgain() {
        givenStatusChange("open", StatusChangeEnum.UNCHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"prevStatus\":\"closed\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_firstStatus_isOnlyAChangeWhenTheDeviceSaysSo() {
        givenDeviceNamed("Gate");
        givenStatusChange("open", StatusChangeEnum.FIRST);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"mode\":\"connect\",\"ts\":" + now + "}");
        verifyPushed("Your Gate is online", deviceId + "-connection");

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"prevStatus\":\"closed\"}");
        verify(firebaseService, times(2)).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_retainedChange_notifiesOnlyWhenRecentlyStamped() {
        givenDeviceNamed("Gate");
        givenStatusChange("open", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"prevStatus\":\"closed\"}", true);
        deviceMessageService.handleMessage(stateTopic,
                "{\"status\":\"open\",\"ts\":" + (now - 3600) + "}", true);
        verify(firebaseService, never()).sendMessage(anyList(), any());

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"ts\":" + (now - 60) + "}", true);
        verifyPushed("Your Gate is open", deviceId + "-status");
    }

    @Test
    void handleMessage_staleLiveChange_isStoredButNotPushed() {
        givenStatusChange("open", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\",\"ts\":" + (now - 3600) + "}");

        verify(homeQService).recordStatus(houseId, deviceId, "open");
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_retainedConnectAndOffline_doNotActivateOrNotify() {
        givenStatusChange("closed", StatusChangeEnum.UNCHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"closed\",\"mode\":\"connect\"}", true);
        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}", true);
        deviceMessageService.handleMessage("home/" + houseId + "/will/" + deviceId, "{\"status\":\"offline\"}", true);

        verify(homeQService, never()).activateAddedDevice(any(), any());
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_offlineStatus_isNotStored() {
        givenDeviceNamed("Gate");

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");

        verify(homeQService, never()).recordStatus(any(), any(), any());
    }

    @Test
    void handleMessage_actionReplies_areNotStored() {
        deviceMessageService.handleMessage(reactionTopic, "{\"id\":\"1\",\"success\":true,\"status\":\"on\"}");

        verify(homeQService, never()).recordStatus(any(), any(), any());
    }

    @Test
    void handleMessage_lightsDoNotPushByDefault() {
        givenDevice("Porch light", DeviceTypeEnum.LIGHT, null, null);
        givenStatusChange("on", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"on\",\"prevStatus\":\"off\"}");
        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");

        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    @Test
    void handleMessage_followsTheDeviceSettings() {
        givenDevice("Porch light", DeviceTypeEnum.LIGHT, true, null);
        givenStatusChange("on", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"on\"}");

        verifyPushed("Your Porch light is on", deviceId + "-status");
    }

    @Test
    void handleMessage_garageWithConnectionPushesOff_stillPushesStatus() {
        givenDevice("Gate", DeviceTypeEnum.GARAGE_DOOR, null, false);
        givenStatusChange("open", StatusChangeEnum.CHANGED);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\"}");
        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"open\"}");

        verifyPushed("Your Gate is open", deviceId + "-status");
    }

    @Test
    void handleMessage_reset_putsTheDeviceBackToSetupAndNotifies() {
        givenDeviceNamed("Gate");
        when(homeQService.markDeviceReset(houseId, deviceId)).thenReturn(true);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\",\"mode\":\"reset\",\"ts\":" + now + "}");

        verifyPushed("Your Gate is reset", deviceId + "-connection");
        verify(homeQService, never()).recordStatus(any(), any(), any());
    }

    @Test
    void handleMessage_retainedOrRepeatedReset_doesNotNotify() {
        when(homeQService.markDeviceReset(houseId, deviceId)).thenReturn(true, false);

        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\",\"mode\":\"reset\"}", true);
        deviceMessageService.handleMessage(stateTopic, "{\"status\":\"offline\",\"mode\":\"reset\"}");

        verify(homeQService, times(2)).markDeviceReset(houseId, deviceId);
        verify(firebaseService, never()).sendMessage(anyList(), any());
    }

    private void givenStatusChange(String status, StatusChangeEnum change) {
        when(homeQService.recordStatus(houseId, deviceId, status)).thenReturn(change);
    }

    private void givenDeviceNamed(String name) {
        givenDevice(name, DeviceTypeEnum.GARAGE_DOOR, null, null);
    }

    private void givenDevice(String name, DeviceTypeEnum type, Boolean notifyStatus, Boolean notifyConnection) {
        when(homeQService.findHouse(houseId)).thenReturn(Optional.of(house(List.of(DeviceModelImmtbl.builder()
                .id(deviceId)
                .name(name)
                .type(type)
                .setupStatus(DeviceSetupStatusEnum.ACTIVE)
                .notifyStatus(Optional.ofNullable(notifyStatus))
                .notifyConnection(Optional.ofNullable(notifyConnection))
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
