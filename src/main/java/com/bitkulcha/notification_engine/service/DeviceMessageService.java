package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.domain.enums.StatusChangeEnum;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.NotificationImmtbl;
import com.bitkulcha.notification_engine.domain.model.PushMessageModelImmtbl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
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
    static final String RESET = "reset";
    static final String STATUS_KEY = "status";
    static final String CONNECTION_KEY = "connection";
    static final String PREV_STATUS_KEY = "prevStatus";
    static final String TIME_KEY = "ts";
    static final Duration FRESH_TIME = Duration.ofMinutes(5);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FirebaseService firebaseService;
    private final HomeQService homeQService;
    Clock clock = Clock.systemUTC();

    public DeviceMessageService(FirebaseService firebaseService, HomeQService homeQService) {
        this.firebaseService = firebaseService;
        this.homeQService = homeQService;
    }

    public void handleMessage(String topic, Object payload) {
        handleMessage(topic, payload, false);
    }

    /**
     * Retained messages are the last state the broker kept, replayed on every (re)subscribe. They only update the
     * stored status, and only push a status change when the device stamped it recently, so a restart of this service
     * neither repeats old pushes nor loses a change it missed while it was down.
     */
    public void handleMessage(String topic, Object payload, boolean retained) {
        log.debug("Message received : {} {} retained {}", topic, payload, retained);
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
                if (RESET.equals(message.get("mode"))) {
                    resetDevice(houseId, deviceId, retained);
                    return;
                }
                if (OFFLINE.equals(message.get("status"))) {
                    if (!retained) notifyBoard(houseId, deviceId, OFFLINE);
                    return;
                }
                if (CONNECT.equals(message.get("mode")) && !retained) {
                    activateConnectedDevice(houseId, deviceId);
                    notify(houseId, deviceId, ONLINE, CONNECTION_KEY);
                }
                recordStatus(houseId, deviceId, message, retained);
            }
            // Devices on firmware from before the state topic, until they are all reflashed.
            case "reaction" -> {
                if (retained) return;
                if (message.get(PREV_STATUS_KEY) != null) recordStatus(houseId, deviceId, message, false);
                if (START.equals(message.get("mode"))) {
                    activateConnectedDevice(houseId, deviceId);
                    notify(houseId, deviceId, ONLINE, CONNECTION_KEY);
                }
            }
            case "will" -> {
                if (!retained) notify(houseId, deviceId,
                        ONLINE.equals(message.get("status")) ? ONLINE : OFFLINE, CONNECTION_KEY);
            }
            default -> { }
        }
    }

    private void recordStatus(UUID houseId, UUID deviceId, Map<String, Object> message, boolean retained) {
        if (!(message.get("status") instanceof String status) || status.isBlank() || OFFLINE.equals(status)) return;
        StatusChangeEnum change;
        try {
            change = homeQService.recordStatus(houseId, deviceId, status);
        } catch (Exception e) {
            log.error("Error recording status {} for device {} in house {}", status, deviceId, houseId, e);
            return;
        }
        if (isChange(change, message, status) && isFresh(message, retained)) {
            notify(houseId, deviceId, status, STATUS_KEY);
        }
    }

    // A first status is only a change when the device says so itself, as firmware sending prevStatus does.
    private static boolean isChange(StatusChangeEnum change, Map<String, Object> message, String status) {
        if (change == StatusChangeEnum.CHANGED) return true;
        if (change != StatusChangeEnum.FIRST) return false;
        Object previous = message.get(PREV_STATUS_KEY);
        return previous instanceof String p && !p.isBlank() && !p.equals(status) && !OFFLINE.equals(p);
    }

    private boolean isFresh(Map<String, Object> message, boolean retained) {
        if (!(message.get(TIME_KEY) instanceof Number time)) return !retained;
        long age = clock.instant().getEpochSecond() - time.longValue();
        return Math.abs(age) <= FRESH_TIME.toSeconds();
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
            send(house, device, status, kind);
        } catch (Exception e) {
            log.error("Error notifying {} for device {} in house {}", status, deviceId, houseId, e);
        }
    }

    // A board has one last will, on its first channel, so going offline covers every channel on the board.
    private void notifyBoard(UUID houseId, UUID deviceId, String status) {
        try {
            HouseModel house = homeQService.findHouse(houseId).orElse(null);
            List<DeviceModel> devices = house == null ? List.of() : house.getDevices().stream()
                    .filter(d -> d.getId().equals(deviceId) || d.getHardwareId().filter(deviceId::equals).isPresent())
                    .toList();
            if (devices.isEmpty()) {
                log.warn("Not notifying {} for unknown device {} in house {}", status, deviceId, houseId);
                return;
            }
            for (DeviceModel device : devices) {
                send(house, device, status, CONNECTION_KEY);
            }
        } catch (Exception e) {
            log.error("Error notifying {} for board {} in house {}", status, deviceId, houseId, e);
        }
    }

    private void send(HouseModel house, DeviceModel device, String status, String kind) {
        boolean wanted = STATUS_KEY.equals(kind) ? device.notifiesStatus() : device.notifiesConnection();
        if (!wanted) {
            log.debug("Not notifying {} for device {}, its {} pushes are off", status, device.getId(), kind);
            return;
        }
        List<String> owners = house.getOwners().stream().map(owner -> owner.getId().toString()).toList();
        firebaseService.sendMessage(owners, PushMessageModelImmtbl.builder()
                .topic(house.getId().toString())
                .collapseKey(device.getId() + "-" + kind)
                .notification(NotificationImmtbl.builder()
                        .title(house.getName())
                        .body("Your " + device.getName() + " is " + status)
                        .build())
                .build());
    }

    private void resetDevice(UUID houseId, UUID deviceId, boolean retained) {
        boolean reset;
        try {
            reset = homeQService.markDeviceReset(houseId, deviceId);
        } catch (Exception e) {
            log.error("Error marking device {} in house {} as reset", deviceId, houseId, e);
            return;
        }
        if (!reset) return;
        log.info("Device {} in house {} was reset, it is waiting for setup again", deviceId, houseId);
        if (!retained) notify(houseId, deviceId, "reset", CONNECTION_KEY);
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
