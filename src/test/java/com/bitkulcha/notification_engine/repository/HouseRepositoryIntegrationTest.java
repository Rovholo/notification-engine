package com.bitkulcha.notification_engine.repository;

import com.bitkulcha.notification_engine.domain.enums.DeviceTypeEnum;
import com.bitkulcha.notification_engine.repository.entity.DeviceEntity;
import com.bitkulcha.notification_engine.repository.entity.HouseEntity;
import com.bitkulcha.notification_engine.repository.entity.UserEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:house-repository-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class HouseRepositoryIntegrationTest {

    @Autowired
    private HouseRepository houseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private UserEntity alice;
    private UserEntity bob;
    private UserEntity charlie;
    private UserEntity dave;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(newUser("Alice"));
        bob = userRepository.save(newUser("Bob"));
        charlie = userRepository.save(newUser("Charlie"));
        dave = userRepository.save(newUser("Dave"));
    }

    @Test
    void findAllByMember_returnsHousesTheUserOwnsOrLivesIn_withAllMembersAndDevices() {
        HouseEntity owned = newHouse("Owned", List.of(alice, bob), List.of(charlie));
        addDevice(owned, "Garage", DeviceTypeEnum.GARAGE_DOOR);
        addDevice(owned, "Porch light", DeviceTypeEnum.LIGHT);
        HouseEntity livedIn = newHouse("Lived in", List.of(bob), List.of(alice, charlie));
        HouseEntity unrelated = newHouse("Unrelated", List.of(bob), List.of(dave));
        houseRepository.saveAll(List.of(owned, livedIn, unrelated));
        entityManager.flush();
        entityManager.clear();

        List<HouseEntity> houses = houseRepository.findAllByMember(alice.getId());

        assertThat(houses).extracting(HouseEntity::getName).containsExactlyInAnyOrder("Owned", "Lived in");
        HouseEntity loadedOwned = houses.stream().filter(h -> h.getName().equals("Owned")).findFirst().orElseThrow();
        // Every member is loaded, not only the row that matched the filter.
        assertThat(loadedOwned.getOwners()).extracting(UserEntity::getName).containsExactlyInAnyOrder("Alice", "Bob");
        assertThat(loadedOwned.getResidents()).extracting(UserEntity::getName).containsExactly("Charlie");
        assertThat(loadedOwned.getDevices()).extracting(DeviceEntity::getName).containsExactlyInAnyOrder("Garage", "Porch light");
        HouseEntity loadedLivedIn = houses.stream().filter(h -> h.getName().equals("Lived in")).findFirst().orElseThrow();
        assertThat(loadedLivedIn.getResidents()).extracting(UserEntity::getName).containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    void findAllByMember_whenUserIsBothOwnerAndResident_returnsHouseOnce() {
        houseRepository.save(newHouse("Home", List.of(alice), List.of(alice, bob)));
        entityManager.flush();
        entityManager.clear();

        assertThat(houseRepository.findAllByMember(alice.getId())).hasSize(1);
    }

    @Test
    void findAllByMember_whenUserHasNoHouses_returnsEmptyList() {
        houseRepository.save(newHouse("Home", List.of(bob), List.of(charlie)));
        entityManager.flush();

        assertThat(houseRepository.findAllByMember(dave.getId())).isEmpty();
    }

    private UserEntity newUser(String name) {
        UserEntity user = new UserEntity();
        user.setName(name);
        user.setSurname("Smith");
        user.setEmail(name.toLowerCase() + "@example.com");
        return user;
    }

    private HouseEntity newHouse(String name, List<UserEntity> owners, List<UserEntity> residents) {
        HouseEntity house = new HouseEntity();
        house.setName(name);
        house.getOwners().addAll(owners);
        house.getResidents().addAll(residents);
        return house;
    }

    private void addDevice(HouseEntity house, String name, DeviceTypeEnum type) {
        DeviceEntity device = new DeviceEntity();
        device.setHouse(house);
        device.setName(name);
        device.setType(type);
        house.getDevices().add(device);
    }
}
