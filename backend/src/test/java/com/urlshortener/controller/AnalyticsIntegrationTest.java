package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Scenario 2 (brownfield): analytics layered on top of the existing create/redirect flow.
 * Click recording is async, so assertions on the stats endpoint poll briefly rather than
 * asserting immediately after the redirect.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void redirectsAreRecordedAndReflectedInStats() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/analytics-target"));
        String response = mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(response).get("code").asText();

        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                mockMvc.perform(get("/api/urls/{code}/stats", code))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalClicks", is(3)))
        );
    }

    @Test
    void statsForUnvisitedUrlShowZeroClicks() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/never-visited"));
        String response = mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(response).get("code").asText();

        mockMvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks", is(0)))
                .andExpect(jsonPath("$.dailyClicks", empty()));
    }

    @Test
    void listMineReturnsOnlyCallersUrlsWithClickCounts() throws Exception {
        String ownerA = "owner-a-" + System.nanoTime();
        String ownerB = "owner-b-" + System.nanoTime();

        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/owner-a-link"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(body).header("X-Owner-Id", ownerA))
                .andExpect(status().isCreated());

        String otherBody = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/owner-b-link"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(otherBody).header("X-Owner-Id", ownerB))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/urls").header("X-Owner-Id", ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalUrl", is("https://example.com/owner-a-link")));
    }
}
