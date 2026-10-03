package com.bitkulcha.notification_engine.domain.mapper;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.domain.model.AccountModel;
import com.bitkulcha.notification_engine.domain.model.BrokerModel;
import com.bitkulcha.notification_engine.domain.model.DeviceModel;
import com.bitkulcha.notification_engine.domain.model.HouseModel;
import com.bitkulcha.notification_engine.domain.model.UserModel;
import com.bitkulcha.notification_engine.repository.entity.BrokerEntity;
import com.bitkulcha.notification_engine.repository.entity.CredentialEntity;
import com.bitkulcha.notification_engine.repository.entity.DeviceEntity;
import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityModelMapperTest {

    @Test
    void toModel_user_mapsAllFields() {
        UserEntity entity = user("Alice", "Smith");
        entity.setCell("0821234567");

        UserModel user = EntityModelMapper.toModel(entity);

        assertThat(user.getId()).isEqualTo(entity.getId());
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getSurname()).isEqualTo("Smith");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getCell()).contains("0821234567");
    }

    @Test
    void toModel_user_withoutCell_hasEmptyCell() {
        assertThat(EntityModelMapper.toModel(user("Alice", "Smith")).getCell()).isEmpty();
    }

    @Test
    void toModel_credential_combinesUsernameAndUser() {
        UserEntity user = user("Alice", "Smith");
        CredentialEntity credential = new CredentialEntity();
        credential.setUsername("alice");
        credential.setUser(user);

        AccountModel account = EntityModelMapper.toModel(credential);

        assertThat(account.getUsername()).isEqualTo("alice");
        assertThat(account.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void toModel_house_mapsMembersAndDevices_sortedByName() {
        UserEntity alice = user("Alice", "Smith");
        UserEntity bobJones = user("Bob", "Jones");
        UserEntity bobAdams = user("Bob", "Adams");
        UserEntity charlie = user("Charlie", "Smith");
        HouseEntity house = new HouseEntity();
        house.setName("Greenwood Manor");
        house.getOwners().addAll(List.of(bobJones, alice, bobAdams));
        house.getResidents().add(charlie);
        house.getDevices().addAll(List.of(
                device(house, "Porch light", DeviceTypeEnum.LIGHT, null),
                device(house, "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed")));

        HouseModel model = EntityModelMapper.toModel(house);

        assertThat(model.getId()).isEqualTo(house.getId());
        assertThat(model.getName()).isEqualTo("Greenwood Manor");
        assertThat(model.getOwners()).extracting(UserModel::getId)
                .containsExactly(alice.getId(), bobAdams.getId(), bobJones.getId());
        assertThat(model.getResidents()).extracting(UserModel::getName).containsExactly("Charlie");
        assertThat(model.getDevices()).extracting(DeviceModel::getName).containsExactly("Garage", "Porch light");
    }

    @Test
    void toModel_device_mapsAllFields() {
        DeviceEntity entity = device(new HouseEntity(), "Garage", DeviceTypeEnum.GARAGE_DOOR, "closed");

        DeviceModel device = EntityModelMapper.toModel(entity);

        assertThat(device.getId()).isEqualTo(entity.getId());
        assertThat(device.getName()).isEqualTo("Garage");
        assertThat(device.getType()).isEqualTo(DeviceTypeEnum.GARAGE_DOOR);
        assertThat(device.getStatus()).contains("closed");
    }

    @Test
    void toModel_device_withoutStatus_hasEmptyStatus() {
        DeviceEntity entity = device(new HouseEntity(), "Light", DeviceTypeEnum.LIGHT, null);

        assertThat(EntityModelMapper.toModel(entity).getStatus()).isEmpty();
    }

    @Test
    void toModel_broker_mapsAllFields() {
        BrokerEntity entity = new BrokerEntity();
        entity.setName("main");
        entity.setServer("broker.example.com");
        entity.setUsername("user");
        entity.setPassword("secret");
        entity.setSecure(true);

        BrokerModel broker = EntityModelMapper.toModel(entity);

        assertThat(broker.getId()).isEqualTo(entity.getId());
        assertThat(broker.getName()).isEqualTo("main");
        assertThat(broker.getServer()).isEqualTo("broker.example.com");
        assertThat(broker.getUsername()).isEqualTo("user");
        assertThat(broker.getPassword()).isEqualTo("secret");
        assertThat(broker.isSecure()).isTrue();
    }

    private UserEntity user(String name, String surname) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname(surname);
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }

    private DeviceEntity device(HouseEntity house, String name, DeviceTypeEnum type, String status) {
        DeviceEntity device = new DeviceEntity();
        device.setHouse(house);
        device.setName(name);
        device.setType(type);
        device.setStatus(status);
        return device;
    }
}
