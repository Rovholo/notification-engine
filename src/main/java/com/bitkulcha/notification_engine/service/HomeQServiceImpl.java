package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.HouseDto;
import com.bitkulcha.notification_engine.dto.HouseDtoImmtbl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class HomeQServiceImpl implements HomeQService {

    final FirebaseService firebaseService;

    public HomeQServiceImpl(FirebaseService firebaseService) {
        this.firebaseService = firebaseService;
    }

    @Override
    public List<HouseDto> getUserHouses(String userId) {
        try {
            userId = firebaseService.getUserIds(userId).stream().findFirst().orElse(userId);

            return firebaseService.getUserHouses(userId);
        } catch (Exception e) {
            log.error(e.getMessage());
            return List.of();
        }

    }

    @Override
    public void addHouseResident(String userId, String homeId) {
        try {
            userId = firebaseService.getUserIds(userId).stream().findFirst().orElse(userId);
            HouseDto house = firebaseService.getHouse(homeId);
            house = HouseDtoImmtbl.copyOf(house)
                    .withResidents(Stream.concat(house.getResidents().stream(), Stream.of(userId)).toList());
            firebaseService.updateHouse(house);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

}
