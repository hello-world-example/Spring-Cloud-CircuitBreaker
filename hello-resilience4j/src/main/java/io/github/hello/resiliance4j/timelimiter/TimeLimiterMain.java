package io.github.hello.resiliance4j.timelimiter;

import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiter.EventPublisher;

public class TimeLimiterMain {
    public static void main(String[] args) {
        // 默认就是一下两个配置
        TimeLimiterConfig defaultTimeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(java.time.Duration.ofSeconds(1))
                .cancelRunningFuture(true)
                .build();

        // TimeLimiter timeLimiter = TimeLimiter.ofDefaults("backendService");
        TimeLimiter timeLimiter = TimeLimiter.of("someService", defaultTimeLimiterConfig);

        timeLimiter.getEventPublisher()
                .onSuccess(event -> System.out.println("Success event: " + event.toString()))
                .onTimeout(event -> System.out.println("Timeout event: " + event.toString()))
                .onError(event -> System.out.println("Error event: " + event.toString()));

        ThreadPoolBulkhead bulkhead = ThreadPoolBulkhead.ofDefaults("aaaa");

        Decorators.ofSupplier(new Supplier<String>() {
            @Override
            public String get() {
                return "Hello World";
            }
        })
                .withThreadPoolBulkhead(bulkhead)
                .withTimeLimiter(timeLimiter, null);

    }
}
