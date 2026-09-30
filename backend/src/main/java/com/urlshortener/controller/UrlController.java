package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.UrlResponse;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.dto.UrlSummaryResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.service.StatsService;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/urls")
@Tag(name = "URLs", description = "Create and inspect shortened URLs")
public class UrlController {

    private final UrlService urlService;
    private final StatsService statsService;
    private final String baseUrl;

    public UrlController(UrlService urlService,
                          StatsService statsService,
                          @Value("${app.base-url}") String baseUrl) {
        this.urlService = urlService;
        this.statsService = statsService;
        this.baseUrl = baseUrl;
    }

    // Header name is fixed to "X-Owner-Id" (annotation values must be compile-time constants);
    // app.owner-id-header in application.yml documents the contract for API consumers.
    @PostMapping
    @Operation(summary = "Create a shortened URL")
    public ResponseEntity<UrlResponse> create(@Valid @RequestBody CreateUrlRequest request,
                                               @RequestHeader(value = "X-Owner-Id", required = false) String ownerId) {
        String resolvedOwnerId = (ownerId == null || ownerId.isBlank()) ? UUID.randomUUID().toString() : ownerId;
        Url url = urlService.create(request, resolvedOwnerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(url));
    }

    @GetMapping
    @Operation(summary = "List URLs created under the caller's X-Owner-Id, with click counts")
    public ResponseEntity<List<UrlSummaryResponse>> listMine(
            @RequestHeader(value = "X-Owner-Id", required = false) String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<UrlSummaryResponse> summaries = urlService.findByOwner(ownerId).stream()
                .map(url -> new UrlSummaryResponse(
                        url.resolvableKey(),
                        baseUrl + "/" + url.resolvableKey(),
                        url.getOriginalUrl(),
                        url.getCreatedAt(),
                        url.getExpiresAt(),
                        statsService.countClicks(url.getId())))
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get metadata for a shortened URL without following the redirect")
    public ResponseEntity<UrlResponse> getMetadata(@PathVariable String code) {
        Url url = urlService.getMetadata(code);
        return ResponseEntity.ok(toResponse(url));
    }

    @GetMapping("/{code}/stats")
    @Operation(summary = "Get click analytics for a shortened URL")
    public ResponseEntity<UrlStatsResponse> getStats(@PathVariable String code) {
        Url url = urlService.getMetadata(code);
        return ResponseEntity.ok(statsService.getStats(url));
    }

    private UrlResponse toResponse(Url url) {
        String key = url.resolvableKey();
        return new UrlResponse(key, baseUrl + "/" + key, url.getOriginalUrl(), url.getCreatedAt(), url.getExpiresAt());
    }
}
