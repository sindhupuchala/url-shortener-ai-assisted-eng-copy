package com.urlshortener.dto;

import java.time.Instant;

public record UrlSummaryResponse(
        String code,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant expiresAt,
        long totalClicks
) {
}
