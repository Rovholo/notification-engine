package com.bitkulcha.notification_engine.service;

import com.bitkulcha.notification_engine.dto.HouseDto;
import com.bitkulcha.notification_engine.dto.HouseDtoImmtbl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HomeQServiceImplTest {

    @Mock
    private FirebaseService firebaseService;

    @InjectMocks
    private HomeQServiceImpl homeQService;

    @Test
    void getUserHouses_resolvesCanonicalUserId_beforeLookingUpHouses() {
        HouseDto house = house("house-1", "Greenwood Manor", List.of("owner-1"), List.of(), List.of());
        when(firebaseService.getUserIds("alice@example.com")).thenReturn(List.of("user-1"));
        when(firebaseService.getUserHouses("user-1")).thenReturn(List.of(house));

        List<HouseDto> result = homeQService.getUserHouses("alice@example.com");

        assertThat(result).containsExactly(house);
        verify(firebaseService).getUserHouses("user-1");
    }

    @Test
    void getUserHouses_whenNoCanonicalIdFound_fallsBackToOriginalId() {
        when(firebaseService.getUserIds("user-1")).thenReturn(List.of());
        when(firebaseService.getUserHouses("user-1")).thenReturn(List.of());

        List<HouseDto> result = homeQService.getUserHouses("user-1");

        assertThat(result).isEmpty();
        verify(firebaseService).getUserHouses("user-1");
    }

    @Test
    void getUserHouses_whenFirebaseServiceThrows_returnsEmptyList() {
        when(firebaseService.getUserIds("user-1")).thenThrow(new RuntimeException("firestore unavailable"));

        List<HouseDto> result = homeQService.getUserHouses("user-1");

        assertThat(result).isEmpty();
        verify(firebaseService, never()).getUserHouses(any());
    }

    @Test
    void addHouseResident_appendsUserToResidentsAndPersistsUpdate() {
        HouseDto house = house("house-1", "Greenwood Manor", List.of("owner-1"), List.of("existing-resident"), List.of("device-1"));
        when(firebaseService.getUserIds("user-1")).thenReturn(List.of("user-1"));
        when(firebaseService.getHouse("house-1")).thenReturn(house);

        homeQService.addHouseResident("user-1", "house-1");

        ArgumentCaptor<HouseDto> captor = ArgumentCaptor.forClass(HouseDto.class);
        verify(firebaseService).updateHouse(captor.capture());
        HouseDto updated = captor.getValue();
        assertThat(updated.getResidents()).containsExactly("existing-resident", "user-1");
        assertThat(updated.getOwners()).containsExactly("owner-1");
        assertThat(updated.getName()).isEqualTo("Greenwood Manor");
    }

    @Test
    void addHouseResident_whenFirebaseServiceThrows_wrapsInRuntimeException() {
        when(firebaseService.getUserIds("user-1")).thenReturn(List.of("user-1"));
        when(firebaseService.getHouse("house-1")).thenThrow(new RuntimeException("firestore unavailable"));

        assertThatThrownBy(() -> homeQService.addHouseResident("user-1", "house-1"))
                .isInstanceOf(RuntimeException.class);

        verify(firebaseService, never()).updateHouse(any());
    }

    private HouseDto house(String id, String name, List<String> owners, List<String> residents, List<String> devices) {
        return HouseDtoImmtbl.builder()
                .id(id)
                .name(name)
                .owners(owners)
                .residents(residents)
                .devices(devices)
                .build();
    }
}