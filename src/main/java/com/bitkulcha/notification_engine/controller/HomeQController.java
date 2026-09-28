package com.bitkulcha.notification_engine.controller;


import com.bitkulcha.notification_engine.api.HomeqApi;
import com.bitkulcha.notification_engine.model.AddResidentRequest;
import com.bitkulcha.notification_engine.model.House;
import com.bitkulcha.notification_engine.service.HomeQService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class HomeQController implements HomeqApi {

    final HomeQService homeQService;

    public HomeQController(HomeQService homeQService) {
        this.homeQService = homeQService;
    }

    @Override
    public ResponseEntity<List<House>> getUserHouses(String userId) {
        List<House> houses = homeQService.getUserHouses(userId).stream().map(houseDto -> {
            House house = new House();
            house.setName(houseDto.getName() + " : " + houseDto.getId());
            return house;
        }).toList();
        return ResponseEntity.ok(houses);
    }

    @Override
    public ResponseEntity<Void> addHouseResident(AddResidentRequest addResidentRequest) {
        homeQService.addHouseResident(addResidentRequest.getUserId(), addResidentRequest.getHomeId());
        return ResponseEntity.ok().build();
    }
}
