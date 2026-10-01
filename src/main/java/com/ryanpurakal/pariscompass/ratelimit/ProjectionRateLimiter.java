package com.ryanpurakal.pariscompass.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryanpurakal.pariscompass.config.AppProperties;
import com.ryanpurakal.pariscompass.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token buckets for the projection endpoint: one per client IP plus one global bucket.
 * Every request counts, including cache hits, so the limit is predictable for clients.
 * Buckets live in memory; with several instances each would enforce its own limits
 * (a shared store such as Redis would be needed for a cluster-wide limit).
 */
@Component
public class ProjectionRateLimiter {
    private final int perClientPerMinute;
    private final Bucket global;
    /** Bounded and expiring, so a flood of distinct IPs cannot grow memory without limit. */
    private final Cache<String, Bucket> perClient = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public ProjectionRateLimiter(AppProperties properties) {
        this.perClientPerMinute = properties.rateLimit().projectionsPerClientPerMinute();
        int globalPerHour = properties.rateLimit().projectionsPerHourGlobal();
        this.global = Bucket.builder()
                .addLimit(l -> l.capacity(globalPerHour).refillGreedy(globalPerHour, Duration.ofHours(1)))
                .build();
    }

    /** Consumes one token from the client's bucket and the global bucket, or throws 429. Returns tokens left for the client. */
    public long acquire(String clientKey) {
        Bucket bucket = perClient.get(clientKey, k -> Bucket.builder()
                .addLimit(l -> l.capacity(perClientPerMinute).refillGreedy(perClientPerMinute, Duration.ofMinutes(1)))
                .build());
        ConsumptionProbe client = bucket.tryConsumeAndReturnRemaining(1);
        if (!client.isConsumed()) {
            throw new RateLimitExceededException("Too many projection requests from this client. Limit: "
                    + perClientPerMinute + " per minute.", seconds(client.getNanosToWaitForRefill()));
        }
        ConsumptionProbe all = global.tryConsumeAndReturnRemaining(1);
        if (!all.isConsumed()) {
            // Not this client's fault: give its token back.
            bucket.addTokens(1);
            throw new RateLimitExceededException("The projection service is at its hourly capacity. Try again later.",
                    seconds(all.getNanosToWaitForRefill()));
        }
        return client.getRemainingTokens();
    }

    private static long seconds(long nanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanos) + 1);
    }
}
