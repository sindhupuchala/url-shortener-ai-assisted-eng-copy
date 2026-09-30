package com.urlshortener.service;

import com.urlshortener.config.CacheConfig;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Caches the DB lookup for a resolvable key so hot redirects don't hit the database every time.
 * Kept separate from {@link UrlService} because Spring's @Cacheable relies on a proxy - calling
 * this method through another bean (not via self-invocation) is what makes caching take effect.
 * Only the raw lookup is cached, never the expiry decision: {@link UrlService#resolve} re-checks
 * expiresAt on every call against the cached entity, so a link can't keep resolving past its
 * expiry just because it's still within the cache TTL.
 */
@Component
public class UrlLookupCache {

    private final UrlRepository urlRepository;

    public UrlLookupCache(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Cacheable(cacheNames = CacheConfig.URL_LOOKUP_CACHE, key = "#key")
    public Url findByKey(String key) {
        return urlRepository.findByResolvableKey(key)
                .orElseThrow(() -> new UrlNotFoundException(key));
    }
}
