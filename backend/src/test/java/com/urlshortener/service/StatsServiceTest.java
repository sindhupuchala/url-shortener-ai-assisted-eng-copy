package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @InjectMocks
    private StatsService statsService;

    @Test
    void aggregatesClicksByDay() {
        Url url = new Url();
        url.setId(1L);
        url.setCode("abc");
        url.setOriginalUrl("https://example.com");
        url.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant today = Instant.now();
        when(clickEventRepository.findByUrlIdOrderByClickedAtAsc(1L)).thenReturn(List.of(
                new ClickEvent(1L, twoDaysAgo, null, "agent-a"),
                new ClickEvent(1L, twoDaysAgo.plusSeconds(60), "ref", "agent-a"),
                new ClickEvent(1L, today, null, "agent-b")
        ));

        var stats = statsService.getStats(url);

        assertThat(stats.totalClicks()).isEqualTo(3);
        assertThat(stats.dailyClicks()).hasSize(2);
        assertThat(stats.dailyClicks().stream().mapToLong(d -> d.clicks()).sum()).isEqualTo(3);
    }

    @Test
    void returnsZeroClicksForUnvisitedUrl() {
        Url url = new Url();
        url.setId(2L);
        url.setCode("xyz");
        url.setOriginalUrl("https://example.com");
        url.setCreatedAt(Instant.now());
        when(clickEventRepository.findByUrlIdOrderByClickedAtAsc(2L)).thenReturn(List.of());

        var stats = statsService.getStats(url);

        assertThat(stats.totalClicks()).isZero();
        assertThat(stats.dailyClicks()).isEmpty();
    }
}
