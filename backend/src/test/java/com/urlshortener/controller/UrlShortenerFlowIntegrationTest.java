package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage of Scenario 1 (greenfield core): create -> redirect -> metadata,
 * plus the validation/conflict/not-found edges the acceptance criteria call for.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createThenRedirectThenFetchMetadata() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/greenfield"));

        String response = mockMvc.perform(post("/api/urls")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", not(emptyString())))
                .andExpect(jsonPath("$.originalUrl", is("https://example.com/greenfield")))
                .andReturn().getResponse().getContentAsString();

        String code = objectMapper.readTree(response).get("code").asText();

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/greenfield"));

        mockMvc.perform(get("/api/urls/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl", is("https://example.com/greenfield")));
    }

    @Test
    void createWithCustomAliasIsResolvableByAlias() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://example.com/aliased", "customAlias", "my-alias-1"));

        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", is("my-alias-1")));

        mockMvc.perform(get("/my-alias-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/aliased"));
    }

    @Test
    void duplicateAliasIsRejectedWithConflict() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://example.com/one", "customAlias", "dup-alias"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        String again = objectMapper.writeValueAsString(
                Map.of("originalUrl", "https://example.com/two", "customAlias", "dup-alias"));
        mockMvc.perform(post("/api/urls").contentType("application/json").content(again))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedUrlIsRejectedWithBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "not-a-url"));

        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", not(empty())));
    }

    @Test
    void unknownCodeReturnsNotFound() throws Exception {
        mockMvc.perform(get("/does-not-exist-xyz"))
                .andExpect(status().isNotFound());
    }
}
