package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrokerRepository  extends JpaRepository<BrokerEntity, UUID> {

    // Ids are time-ordered (UUIDv7), so ordering by id puts the oldest broker first.
    List<BrokerEntity> findByNameOrderByIdAsc(String name);
}
