package com.urlshortener.dto;

import com.urlshortener.validation.ValidUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(

        @ValidUrl
        String originalUrl,

        @Size(min = 3, max = 32, message = "must be between 3 and 32 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "must contain only letters, digits, '_' or '-'")
        String customAlias,

        @Future(message = "must be in the future")
        Instant expiresAt
) {
}
