package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HouseRepository  extends JpaRepository<HouseEntity, UUID> {

    // The entity graph loads every owner, resident and device, not just the rows matched by the filter joins.
    @EntityGraph(attributePaths = {"owners", "residents", "devices"})
    @Query("SELECT DISTINCT h FROM HouseEntity h LEFT JOIN h.owners o LEFT JOIN h.residents r "
            + "WHERE o.id = :userId OR r.id = :userId")
    List<HouseEntity> findAllByMember(UUID userId);
}
