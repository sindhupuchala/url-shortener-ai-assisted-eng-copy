package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Records a click off the request thread so redirect latency is unaffected by analytics
 * writes. Trade-off: a click can be lost if the process crashes between the redirect response
 * being sent and this async write landing (eventual consistency, not exactly-once) - acceptable
 * for click counts, documented in docs/SCENARIOS.md.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final ClickEventRepository clickEventRepository;

    public ClickRecorder(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    @Async
    public void record(Long urlId, String referrer, String userAgent) {
        try {
            clickEventRepository.save(new ClickEvent(urlId, Instant.now(), referrer, userAgent));
        } catch (Exception e) {
            log.warn("Failed to record click for urlId={}", urlId, e);
        }
    }
}
