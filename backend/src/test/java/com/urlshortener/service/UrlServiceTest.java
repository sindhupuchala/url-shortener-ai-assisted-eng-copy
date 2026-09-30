package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.entity.Url;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private ShortCodeEncoder shortCodeEncoder;

    @Mock
    private UrlLookupCache urlLookupCache;

    @InjectMocks
    private UrlService urlService;

    @Test
    void createAssignsCodeDerivedFromGeneratedId() {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/very/long/path", null, null);
        when(urlRepository.saveAndFlush(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setId(42L);
            return url;
        });
        when(shortCodeEncoder.encode(42L)).thenReturn("g8");
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Url result = urlService.create(request, "owner-1");

        assertThat(result.getCode()).isEqualTo("g8");
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com/very/long/path");
        assertThat(result.getOwnerId()).isEqualTo("owner-1");
        verify(urlRepository).saveAndFlush(any(Url.class));
        verify(urlRepository).save(any(Url.class));
    }

    @Test
    void createRejectsDuplicateCustomAlias() {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com", "taken", null);
        when(urlRepository.existsByCustomAlias("taken")).thenReturn(true);

        assertThatThrownBy(() -> urlService.create(request, "owner-1"))
                .isInstanceOf(AliasAlreadyExistsException.class);
        verify(urlRepository, never()).saveAndFlush(any());
    }

    @Test
    void resolveThrowsWhenCodeUnknown() {
        when(urlLookupCache.findByKey("missing")).thenThrow(new UrlNotFoundException("missing"));

        assertThatThrownBy(() -> urlService.resolve("missing"))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void resolveThrowsWhenLinkExpired() {
        Url expired = new Url();
        expired.setCode("abc");
        expired.setOriginalUrl("https://example.com");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(urlLookupCache.findByKey("abc")).thenReturn(expired);

        assertThatThrownBy(() -> urlService.resolve("abc"))
                .isInstanceOf(UrlExpiredException.class);
    }

    @Test
    void resolveReturnsUrlWhenNotExpired() {
        Url active = new Url();
        active.setCode("abc");
        active.setOriginalUrl("https://example.com");
        active.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(urlLookupCache.findByKey("abc")).thenReturn(active);

        Url result = urlService.resolve("abc");

        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
    }
}
