package com.bitkulcha.notification_engine.repository.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "credential")
public class CredentialEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @Column(nullable = false, unique = true, length = 65)
    private String username;

    @Column(nullable = false)
    private String password;

    @OneToOne
    @JoinColumn(name = "id")
    private UserEntity user;
}
