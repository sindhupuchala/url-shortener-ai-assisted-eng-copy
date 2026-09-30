package com.urlshortener.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public class ValidUrlValidator implements ConstraintValidator<ValidUrl, String> {

    // Tightened as part of the Scenario 3 abuse-hardening pass: the original check only required
    // *some* absolute scheme, which would have accepted javascript:/data:/file: URIs - fine for a
    // syntax check, unsafe for a service that redirects the browser to arbitrary user input.
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return uri.isAbsolute()
                    && uri.getScheme() != null
                    && ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
