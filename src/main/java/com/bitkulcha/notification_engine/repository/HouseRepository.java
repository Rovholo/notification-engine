package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface HouseRepository  extends JpaRepository<HouseEntity, UUID> {
}
