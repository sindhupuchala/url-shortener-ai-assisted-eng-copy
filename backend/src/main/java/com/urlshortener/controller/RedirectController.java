package com.urlshortener.controller;

import com.urlshortener.entity.Url;
import com.urlshortener.service.ClickRecorder;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Tag(name = "Redirect", description = "Resolves a short code and redirects to the original URL")
public class RedirectController {

    private final UrlService urlService;
    private final ClickRecorder clickRecorder;

    public RedirectController(UrlService urlService, ClickRecorder clickRecorder) {
        this.urlService = urlService;
        this.clickRecorder = clickRecorder;
    }

    @GetMapping("/{code}")
    @Operation(summary = "Redirect to the original URL for a short code or custom alias")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        Url url = urlService.resolve(code);
        // Fire-and-forget: click recording must never add latency to the redirect itself.
        clickRecorder.record(url.getId(), request.getHeader(HttpHeaders.REFERER), request.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url.getOriginalUrl()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
