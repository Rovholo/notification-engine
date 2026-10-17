package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.NotificationImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModelImmtbl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DeviceMessageService {

    static final String ONLINE = "online";
    static final String OFFLINE = "offline";
    static final String CONNECT = "connect";
    static final String START = "start";
    static final String STATUS_KEY = "status";
    static final String CONNECTION_KEY = "connection";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FirebaseService firebaseService;
    private final HomeQService homeQService;

    public DeviceMessageService(FirebaseService firebaseService, HomeQService homeQService) {
        this.firebaseService = firebaseService;
        this.homeQService = homeQService;
    }

    public void handleMessage(String topic, Object payload) {
        log.debug("Message received : {} {}", topic, payload);
        if (topic == null) return;
        String[] tokens = topic.split("/");
        if (tokens.length != 4 || !"home".equals(tokens[0])) return;
        UUID houseId;
        UUID deviceId;
        Map<String, Object> message;
        try {
            houseId = UUID.fromString(tokens[1]);
            deviceId = UUID.fromString(tokens[3]);
            message = objectMapper.readValue((String) payload, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Error reading device message from topic: {} {}", topic, payload, e);
            return;
        }
        switch (tokens[2]) {
            case "state" -> {
                // Each step catches its own errors, so a failed notification never stops a device being activated.
                if (OFFLINE.equals(message.get("status"))) {
                    notify(houseId, deviceId, OFFLINE, CONNECTION_KEY);
                } else if (CONNECT.equals(message.get("mode"))) {
                    activateConnectedDevice(houseId, deviceId);
                    notify(houseId, deviceId, ONLINE, CONNECTION_KEY);
                } else if (message.get("prevStatus") != null) {
                    notify(houseId, deviceId, (String) message.get("status"), STATUS_KEY);
                }
            }
            // Devices on firmware from before the state topic, until they are all reflashed.
            case "reaction" -> {
                if (message.get("prevStatus") != null) {
                    notify(houseId, deviceId, (String) message.get("status"), STATUS_KEY);
                }
                if (START.equals(message.get("mode"))) {
                    activateConnectedDevice(houseId, deviceId);
                    notify(houseId, deviceId, ONLINE, CONNECTION_KEY);
                }
            }
            case "will" -> notify(houseId, deviceId,
                    ONLINE.equals(message.get("status")) ? ONLINE : OFFLINE, CONNECTION_KEY);
            default -> { }
        }
    }

    private void notify(UUID houseId, UUID deviceId, String status, String kind) {
        try {
            HouseModel house = homeQService.findHouse(houseId).orElse(null);
            DeviceModel device = house == null ? null : house.getDevices().stream()
                    .filter(d -> d.getId().equals(deviceId))
                    .findFirst()
                    .orElse(null);
            if (device == null) {
                log.warn("Not notifying {} for unknown device {} in house {}", status, deviceId, houseId);
                return;
            }
            List<String> owners = house.getOwners().stream().map(owner -> owner.getId().toString()).toList();
            firebaseService.sendMessage(owners, PushMessageModelImmtbl.builder()
                    .topic(houseId.toString())
                    .collapseKey(deviceId + "-" + kind)
                    .notification(NotificationImmtbl.builder()
                            .title(house.getName())
                            .body("Your " + device.getName() + " is " + status)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("Error notifying {} for device {} in house {}", status, deviceId, houseId, e);
        }
    }

    private void activateConnectedDevice(UUID houseId, UUID deviceId) {
        try {
            if (homeQService.activateAddedDevice(houseId, deviceId)) {
                log.info("Activated device {} in house {} after it connected", deviceId, houseId);
            }
        } catch (Exception e) {
            log.error("Error activating device {} in house {}", deviceId, houseId, e);
        }
    }
}
