package com.urlshortener.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** One structured log line per request on the two highest-traffic paths: create and redirect. */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.urlshortener.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (isTracked(request)) {
                long durationMs = System.currentTimeMillis() - start;
                log.info("method={} path={} status={} durationMs={} remoteAddr={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, request.getRemoteAddr());
            }
        }
    }

    private boolean isTracked(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/urls") || isRedirectPath(path);
    }

    private boolean isRedirectPath(String path) {
        return path.matches("^/[^/]+$") && !path.equals("/swagger-ui.html");
    }
}
