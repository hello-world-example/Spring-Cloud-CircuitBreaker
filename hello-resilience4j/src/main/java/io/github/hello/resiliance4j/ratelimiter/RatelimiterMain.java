package io.github.hello.resiliance4j.ratelimiter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

public class RatelimiterMain {
    public static void main(String[] args) {
        RateLimiterConfig defaultRateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(java.time.Duration.ofSeconds(1))
                .timeoutDuration(java.time.Duration.ofMillis(500))
                .build();

        RateLimiter rateLimiter = RateLimiter.ofDefaults("backendService");

        rateLimiter.getEventPublisher()
                .onSuccess(event -> System.out.println("Success event: " + event.toString()))
                .onFailure(event -> System.out.println("Failure event: " + event.toString()));
    }
}
