package com.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsRequestsUpToCapacityThenRejects() {
        RateLimiter limiter = new RateLimiter(3, 60);

        assertThat(limiter.tryAcquire("client-a")).isEqualTo(0);
        assertThat(limiter.tryAcquire("client-a")).isEqualTo(0);
        assertThat(limiter.tryAcquire("client-a")).isEqualTo(0);
        assertThat(limiter.tryAcquire("client-a")).isGreaterThan(0);
    }

    @Test
    void tracksEachKeyIndependently() {
        RateLimiter limiter = new RateLimiter(1, 60);

        assertThat(limiter.tryAcquire("client-a")).isEqualTo(0);
        assertThat(limiter.tryAcquire("client-a")).isGreaterThan(0);
        assertThat(limiter.tryAcquire("client-b")).isEqualTo(0);
    }
}
