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

/**
 * Handles every message received over mqtt, and calls the services that act on it. Devices publish on
 * {@code home/<houseId>/reaction/<deviceId>}:
 * <ul>
 *     <li>a status change carries the previous status in {@code "prevStatus"}, and the house owners get a push
 *     notification;</li>
 *     <li>{@code "mode": "start"} is sent each time the device boots with its details, so a device that is still
 *     ADDED (e.g. the app lost its access point before the device replied) is marked ACTIVE, and the house owners
 *     are told it is online.</li>
 * </ul>
 * The broker publishes a device's last will, {@code "status": "offline"}, on {@code home/<houseId>/will/<deviceId>}
 * when it drops off without disconnecting. The device publishes {@code "status": "online"} there when it reconnects
 * without rebooting. The house owners are told either way.
 * <p>
 * Each device has two notifications on the phone: its status (e.g. open) and its connection (online or offline). A
 * new one replaces only the old one of its kind, so going offline never hides that the device was left open.
 */
@Slf4j
@Service
public class DeviceMessageService {

    static final String ONLINE = "online";
    static final String OFFLINE = "offline";
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
            case "reaction" -> {
                // Each step catches its own errors, so a failed notification never stops a device being activated.
                if (message.get("prevStatus") != null) {
                    notify(houseId, deviceId, (String) message.get("status"), STATUS_KEY);
                }
                if ("start".equals(message.get("mode"))) {
                    activateStartedDevice(houseId, deviceId);
                    notify(houseId, deviceId, ONLINE, CONNECTION_KEY);
                }
            }
            case "will" -> notify(houseId, deviceId,
                    ONLINE.equals(message.get("status")) ? ONLINE : OFFLINE, CONNECTION_KEY);
            default -> { }
        }
    }

    /**
     * Tells the owners of the house that the device changed status. The app subscribes each user to their own id.
     * The notification replaces the device's previous one of the same {@code kind}.
     */
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

    private void activateStartedDevice(UUID houseId, UUID deviceId) {
        try {
            if (homeQService.activateAddedDevice(houseId, deviceId)) {
                log.info("Activated device {} in house {} after it started", deviceId, houseId);
            }
        } catch (Exception e) {
            log.error("Error activating device {} in house {}", deviceId, houseId, e);
        }
    }
}
