package com.ryanpurakal.pariscompass.ratelimit;

import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionRateLimiterTest {

    private static ProjectionRateLimiter limiter(int perClient, int global) {
        return new ProjectionRateLimiter(new AppProperties(new AppProperties.Cors(List.of("http://x")),
                new AppProperties.Gemini("m", "", false), null, null, new AppProperties.RateLimit(perClient, global)));
    }

    @Test
    void perClientLimitIsIndependentPerClient() {
        ProjectionRateLimiter l = limiter(2, 100);
        assertThat(l.acquire("a")).isEqualTo(1);
        assertThat(l.acquire("a")).isZero();
        assertThatThrownBy(() -> l.acquire("a"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("2 per minute")
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isBetween(1L, 60L));
        assertThat(l.acquire("b")).isEqualTo(1);
    }

    @Test
    void globalCapStopsManyClientsAndRefundsTheClientToken() {
        ProjectionRateLimiter l = limiter(5, 3);
        l.acquire("a");
        l.acquire("b");
        l.acquire("c");
        assertThatThrownBy(() -> l.acquire("d")).hasMessageContaining("hourly capacity");
        // d's own token was given back: it still has all 5 once global capacity exists.
        assertThatThrownBy(() -> l.acquire("d")).hasMessageContaining("hourly capacity");
    }
}
