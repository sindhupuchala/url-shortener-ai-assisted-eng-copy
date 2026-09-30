package com.urlshortener.service;

import org.springframework.stereotype.Component;

/**
 * Encodes a positive DB id into a base62 short code. Deterministic and collision-free by
 * construction as long as the underlying id is unique (delegated to the DB's IDENTITY column),
 * which avoids the retry-on-collision loop a random-code generator would need.
 */
@Component
public class ShortCodeEncoder {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    public String encode(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive, got " + id);
        }
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(ALPHABET.charAt(remainder));
            value /= BASE;
        }
        return sb.reverse().toString();
    }
}
