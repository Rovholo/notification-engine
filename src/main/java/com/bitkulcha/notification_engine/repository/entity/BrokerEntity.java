package com.bitkulcha.notification_engine.repository.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "brokers")
@EntityListeners(AuditingEntityListener.class)
public class BrokerEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 65)
    private String username;

    @Column(nullable = false, length = 65)
    private String password;

    @Column(nullable = false)
    private String server;

    @Column(nullable = false)
    private Boolean secure;

    @LastModifiedBy
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "updated_by", length = 36)
    private UUID updatedBy;

}
