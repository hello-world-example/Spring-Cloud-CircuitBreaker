package io.github.hello.resiliance4j;

import java.util.List;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;

public class Main {
    public static void main(String[] args) {
        Supplier<String> supplier = () -> "Hello World";

        String result = Decorators.ofSupplier(supplier)
                .withBulkhead(Bulkhead.ofDefaults("name"))
                .withCircuitBreaker(CircuitBreaker.ofDefaults("name"))
                .withRetry(Retry.ofDefaults("name"))
                .withFallback(List.of(CallNotPermittedException.class, BulkheadFullException.class),
                        throwable -> "Hello from fallback")
                .get();

        System.out.println(result);
    }
}