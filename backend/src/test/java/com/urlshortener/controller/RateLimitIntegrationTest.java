package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs with its own tiny rate-limit capacity (via distinct @SpringBootTest properties, which
 * gives it its own Spring context and thus its own RateLimiter instance) so it doesn't share -
 * or get starved by - the token bucket used by every other integration test's POST /api/urls
 * calls against the default-configuration context.
 */
@SpringBootTest(properties = {"app.rate-limit.capacity=3", "app.rate-limit.refill-per-minute=3"})
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exceedingCreateRateLimitReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/rl-" + i));
            mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                    .andExpect(status().isCreated());
        }

        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/rl-over"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
