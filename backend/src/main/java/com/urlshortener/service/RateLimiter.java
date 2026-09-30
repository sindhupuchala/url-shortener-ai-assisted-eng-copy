package com.urlshortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-key token bucket. Single-node only by design (state lives in a local map) - a
 * multi-instance deployment would need this backed by Redis (e.g. INCR + EXPIRE, or a Lua token
 * bucket) so limits are shared across instances. Documented trade-off in docs/SCENARIOS.md.
 */
@Component
public class RateLimiter {

    private final double capacity;
    private final double refillPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.rate-limit.capacity}") double capacity,
                        @Value("${app.rate-limit.refill-per-minute}") double refillPerMinute) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerMinute / 60.0;
    }

    /** Returns 0 if the request is allowed, or the number of seconds to wait before retrying. */
    public long tryAcquire(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, System.nanoTime()));
        synchronized (bucket) {
            refill(bucket);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return 0;
            }
            double deficit = 1.0 - bucket.tokens;
            return (long) Math.ceil(deficit / refillPerSecond);
        }
    }

    private void refill(Bucket bucket) {
        long now = System.nanoTime();
        double elapsedSeconds = (now - bucket.lastRefillNanos) / 1_000_000_000.0;
        bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
        bucket.lastRefillNanos = now;
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
