package com.urlshortener.service;

import com.urlshortener.dto.DailyClickCount;
import com.urlshortener.dto.UrlStatsResponse;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final UrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    public StatsService(UrlRepository urlRepository, ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(Url url) {
        List<ClickEvent> events = clickEventRepository.findByUrlIdOrderByClickedAtAsc(url.getId());

        // Aggregated in the service layer rather than via a GROUP BY query: simplest portable
        // option (works identically on H2/Postgres) at prototype click volumes; would move to a
        // SQL aggregation or a rollup table if a single link's click history grew very large.
        Map<LocalDate, Long> byDay = events.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getClickedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()));

        List<DailyClickCount> dailyClicks = byDay.entrySet().stream()
                .map(entry -> new DailyClickCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(DailyClickCount::date))
                .toList();

        return new UrlStatsResponse(url.resolvableKey(), url.getOriginalUrl(), events.size(), url.getCreatedAt(), dailyClicks);
    }

    @Transactional(readOnly = true)
    public long countClicks(Long urlId) {
        return clickEventRepository.countByUrlId(urlId);
    }
}
