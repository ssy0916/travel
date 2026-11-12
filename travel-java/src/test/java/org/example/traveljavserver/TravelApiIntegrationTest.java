package org.example.traveljavserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:travel-api-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "server.llm.api-key=",
        "server.amap.api-key="
})
@AutoConfigureMockMvc
@Transactional
class TravelApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.example.traveljavserver.repository.TripRepository tripRepository;

    @BeforeEach
    void clearTrips() {
        tripRepository.deleteAll();
    }

    @Test
    void recommendationFallsBackWhenExternalKeysAreMissing() throws Exception {
        mockMvc.perform(post("/api/travel/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"武汉","days":1,"totalBudget":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degraded").value(true))
                .andExpect(jsonPath("$.data.days[0].slots", hasSize(3)));
    }

    @Test
    void invalidRecommendationRequestReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/travel/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city":"","days":null,"totalBudget":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void tripApiDeduplicatesAndIsolatesDevices() throws Exception {
        String request = """
                {"itinerary":{"city":"武汉","day":1,"budget":1000,"summary":"周末行程"}}
                """;

        mockMvc.perform(post("/api/travel/trips")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(false))
                .andReturn();

        mockMvc.perform(post("/api/travel/trips")
                        .header("X-Device-Id", "device-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(true));

        mockMvc.perform(get("/api/travel/trips")
                        .header("X-Device-Id", "device-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/travel/trips")
                        .header("X-Device-Id", "device-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
