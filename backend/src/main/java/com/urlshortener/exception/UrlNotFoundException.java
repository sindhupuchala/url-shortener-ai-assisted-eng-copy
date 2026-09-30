package com.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException {
    public UrlNotFoundException(String key) {
        super("No URL found for '" + key + "'");
    }
}
