package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UrlService {

    private final UrlRepository urlRepository;
    private final ShortCodeEncoder shortCodeEncoder;
    private final UrlLookupCache urlLookupCache;

    public UrlService(UrlRepository urlRepository, ShortCodeEncoder shortCodeEncoder, UrlLookupCache urlLookupCache) {
        this.urlRepository = urlRepository;
        this.shortCodeEncoder = shortCodeEncoder;
        this.urlLookupCache = urlLookupCache;
    }

    @Transactional
    public Url create(CreateUrlRequest request, String ownerId) {
        if (request.customAlias() != null && urlRepository.existsByCustomAlias(request.customAlias())) {
            throw new AliasAlreadyExistsException(request.customAlias());
        }

        Url url = new Url();
        url.setOriginalUrl(request.originalUrl());
        url.setCustomAlias(request.customAlias());
        url.setOwnerId(ownerId);
        url.setCreatedAt(Instant.now());
        url.setExpiresAt(request.expiresAt());

        // First insert produces the IDENTITY id; the code (base62 of that id) can only be
        // computed afterwards, so it is assigned in a follow-up update within this same
        // transaction. See the nullability note on Url.code.
        urlRepository.saveAndFlush(url);
        url.setCode(shortCodeEncoder.encode(url.getId()));
        return urlRepository.save(url);
    }

    @Transactional(readOnly = true)
    public Url resolve(String key) {
        Url url = urlLookupCache.findByKey(key);
        if (url.isExpired()) {
            throw new UrlExpiredException(key);
        }
        return url;
    }

    @Transactional(readOnly = true)
    public Url getMetadata(String key) {
        return urlRepository.findByResolvableKey(key)
                .orElseThrow(() -> new UrlNotFoundException(key));
    }

    @Transactional(readOnly = true)
    public List<Url> findByOwner(String ownerId) {
        return urlRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }
}
