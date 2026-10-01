package com.ryanpurakal.pariscompass.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the projection rate limit before the controller runs. An interceptor (rather than a servlet
 * filter) lets a RateLimitExceededException flow through GlobalExceptionHandler like any other error.
 * The client key is the remote address, which in prod Tomcat's RemoteIpValve derives from X-Forwarded-For.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final ProjectionRateLimiter limiter;

    public RateLimitInterceptor(ProjectionRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("POST".equals(request.getMethod())) {
            long remaining = limiter.acquire(request.getRemoteAddr());
            response.setHeader(REMAINING_HEADER, String.valueOf(remaining));
        }
        return true;
    }
}
