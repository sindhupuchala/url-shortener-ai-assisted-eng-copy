package com.urlshortener.dto;

import java.time.Instant;
import java.util.List;

public record UrlStatsResponse(
        String code,
        String originalUrl,
        long totalClicks,
        Instant createdAt,
        List<DailyClickCount> dailyClicks
) {
}
