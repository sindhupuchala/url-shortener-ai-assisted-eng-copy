package com.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException(String key) {
        super("Link '" + key + "' has expired");
    }
}
