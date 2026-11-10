package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    @Query("SELECT d FROM DeviceEntity d WHERE d.id = :deviceId AND d.house.id = :houseId")
    Optional<DeviceEntity> findInHouse(UUID houseId, UUID deviceId);

    // Only moves the status on from the one that was read, so two copies of a message cannot both count as a change.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE DeviceEntity d SET d.status = :status WHERE d.id = :deviceId AND d.house.id = :houseId "
            + "AND d.status = :previous")
    int replaceStatus(UUID houseId, UUID deviceId, String previous, String status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE DeviceEntity d SET d.status = :status WHERE d.id = :deviceId AND d.house.id = :houseId "
            + "AND d.status IS NULL")
    int setFirstStatus(UUID houseId, UUID deviceId, String status);
}
