package com.urlshortener.service;

import com.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Expired links already 410 at resolve time (see UrlService#resolve); this job just reclaims
 * storage for links that have been expired for a while, on a fixed daily schedule. Retention
 * (not immediate deletion) leaves a window to investigate/restore a link expired by mistake.
 */
@Component
public class ExpiredUrlCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredUrlCleanupJob.class);

    private final UrlRepository urlRepository;
    private final int retentionDays;

    public ExpiredUrlCleanupJob(UrlRepository urlRepository,
                                 @Value("${app.cleanup.expired-retention-days}") int retentionDays) {
        this.urlRepository = urlRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeLongExpiredUrls() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = urlRepository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} url(s) expired before {}", deleted, cutoff);
        }
    }
}
