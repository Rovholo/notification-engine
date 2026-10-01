package com.bitkulcha.notification_engine.repository.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "houses")
public class HouseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private UUID id = UuidCreator.getTimeOrderedEpoch();

    @Column(nullable = false)
    private String name;

    @ManyToMany
    @JoinTable(name = "house_owners",
            joinColumns = @JoinColumn(name = "house_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<UserEntity> owners = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "house_residents",
            joinColumns = @JoinColumn(name = "house_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<UserEntity> residents = new HashSet<>();

    @OneToMany(mappedBy = "house", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DeviceEntity> devices = new HashSet<>();
}