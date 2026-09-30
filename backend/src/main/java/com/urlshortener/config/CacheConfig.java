package com.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String URL_LOOKUP_CACHE = "urlLookup";

    @Bean
    public CacheManager cacheManager(@Value("${app.redirect-cache.max-size}") long maxSize,
                                      @Value("${app.redirect-cache.ttl-minutes}") long ttlMinutes) {
        CaffeineCacheManager manager = new CaffeineCacheManager(URL_LOOKUP_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES));
        return manager;
    }
}
