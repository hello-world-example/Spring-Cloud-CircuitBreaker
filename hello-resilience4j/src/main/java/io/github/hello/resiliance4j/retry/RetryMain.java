package io.github.hello.resiliance4j.retry;

import io.github.resilience4j.retry.Retry;

public class RetryMain {
    public static void main(String[] args) {
        Retry retry = Retry.ofDefaults("backendService");
        retry.getEventPublisher()
                .onRetry(event -> System.out.println("Retry event: " + event.toString()))
                .onSuccess(event -> System.out.println("Success event: " + event.toString()))
                .onError(event -> System.out.println("Error event: " + event.toString()));
    }
}
