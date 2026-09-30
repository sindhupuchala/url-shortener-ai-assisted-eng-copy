package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenario 3 (ambiguous "make sure this can survive real traffic and doesn't get abused"),
 * covering two of the acceptance criteria the vague requirement was normalized into: unsafe
 * URL schemes are rejected, and an expired link 410s instead of redirecting. Rate limiting is
 * covered separately in {@link RateLimitIntegrationTest} with its own isolated, low-capacity
 * context so it doesn't compete for tokens with the POSTs in this class or in the other
 * greenfield/brownfield integration tests that share the default Spring context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReliabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void rejectsJavascriptSchemeWithBadRequest() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "javascript:alert(document.cookie)"));

        mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void expiredLinkReturnsGoneInsteadOfRedirecting() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("originalUrl", "https://example.com/will-expire"));
        String response = mockMvc.perform(post("/api/urls").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = objectMapper.readTree(response).get("code").asText();

        Url url = urlRepository.findByCode(code).orElseThrow();
        url.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        urlRepository.save(url);

        mockMvc.perform(get("/{code}", code)).andExpect(status().isGone());
    }
}
