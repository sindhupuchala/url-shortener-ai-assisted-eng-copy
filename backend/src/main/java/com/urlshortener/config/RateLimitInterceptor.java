package com.urlshortener.config;

import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies the per-IP creation rate limit defined in Scenario 3's acceptance criteria. */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // request.getRemoteAddr() sees the immediate peer, not the original client, if a reverse
        // proxy sits in front of this service - a production deployment behind one would need to
        // trust and parse X-Forwarded-For instead. Acceptable for this single-node prototype.
        String clientKey = request.getRemoteAddr();
        long retryAfterSeconds = rateLimiter.tryAcquire(clientKey);
        if (retryAfterSeconds > 0) {
            throw new RateLimitExceededException(retryAfterSeconds);
        }
        return true;
    }
}
