package com.bitkulcha.notification_engine.controller;

import com.bitkulcha.notification_engine.dto.HouseDto;
import com.bitkulcha.notification_engine.dto.HouseDtoImmtbl;
import com.bitkulcha.notification_engine.model.AddResidentRequest;
import com.bitkulcha.notification_engine.model.House;
import com.bitkulcha.notification_engine.service.HomeQService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeQControllerTest {

    @Mock
    private HomeQService homeQService;

    @InjectMocks
    private HomeQController homeQController;

    @Test
    void getUserHouses_mapsServiceHousesToNameAndId() {
        HouseDto house = HouseDtoImmtbl.builder()
                .id("house-1")
                .name("Greenwood Manor")
                .owners(List.of("owner-1"))
                .residents(List.of())
                .devices(List.of())
                .build();
        when(homeQService.getUserHouses("user-1")).thenReturn(List.of(house));

        ResponseEntity<List<House>> response = homeQController.getUserHouses("user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().getName()).isEqualTo("Greenwood Manor : house-1");
    }

    @Test
    void addHouseResident_delegatesToServiceAndReturnsOk() {
        AddResidentRequest request = new AddResidentRequest("user-1", "house-1");

        ResponseEntity<Void> response = homeQController.addHouseResident(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(homeQService).addHouseResident("user-1", "house-1");
    }
}