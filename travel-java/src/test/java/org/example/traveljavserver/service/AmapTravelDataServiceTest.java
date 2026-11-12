package org.example.traveljavserver.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmapTravelDataServiceTest {

    @Test
    void providerFailureFallsBackWithoutDiscardingOtherData() {
        AmapClient client = mock(AmapClient.class);
        when(client.geocode("武汉")).thenThrow(new IllegalStateException("provider unavailable"));
        when(client.searchPois(null, "110000", "武汉", 20))
                .thenThrow(new IllegalStateException("POI unavailable"));
        when(client.searchPois("酒店", "080000", "武汉", 10))
                .thenReturn(List.of(new AmapClient.Poi(
                        "hotel-1", "测试酒店", "住宿", "武汉", 114.3, 30.6,
                        null, null, null, null, null)));

        AmapTravelDataService service = new AmapTravelDataService(client);
        AmapTravelDataService.TravelData result = service.load("武汉");

        assertNull(result.location());
        assertEquals(List.of(), result.candidates());
        assertEquals(List.of(), result.weather());
        assertEquals(1, result.hotels().size());
        assertEquals("测试酒店", result.hotels().getFirst().name());
    }
}
