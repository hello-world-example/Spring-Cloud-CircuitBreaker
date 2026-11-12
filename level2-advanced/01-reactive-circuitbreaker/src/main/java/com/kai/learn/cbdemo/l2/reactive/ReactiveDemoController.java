package com.kai.learn.cbdemo.l2.reactive;

import java.time.Duration;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 观察接口：返回值都是 Mono / Flux，由 WebFlux 负责订阅。
 */
@RestController
public class ReactiveDemoController {

    private final ReactiveProductService service;
    private final CircuitBreakerRegistry registry;

    public ReactiveDemoController(ReactiveProductService service, CircuitBreakerRegistry registry) {
        this.service = service;
        this.registry = registry;
    }

    @GetMapping("/demo/call")
    public Mono<ApiResult<Map<String, Object>>> call() {
        return service.getProduct();
    }

    /**
     * 顺序调用 n 次。concatMap 保证上一次完成后才发起下一次，和 L1 的 for 循环等价，便于观察状态切换。
     */
    @GetMapping("/demo/burst")
    public Flux<CallRecord> burst(@RequestParam(defaultValue = "12") int n,
                                  @RequestParam(defaultValue = "100") long intervalMs) {
        return Flux.range(1, n)
                .concatMap(i -> {
                    long start = System.nanoTime();
                    return service.getProduct()
                            .map(r -> new CallRecord(i, r.source(), r.message(),
                                    registry.circuitBreaker("reactive").getState().name(),
                                    (System.nanoTime() - start) / 1_000_000,
                                    r.data() == null ? "-" : String.valueOf(r.data().get("handledBy"))))
                            .delayElement(Duration.ofMillis(intervalMs));
                });
    }

    @GetMapping("/demo/timeout")
    public Mono<Map<String, Object>> timeout(@RequestParam(defaultValue = "1000") long delayMs) {
        long start = System.nanoTime();
        return service.slow(delayMs)
                .map(r -> Map.of("result", r, "costMs", (System.nanoTime() - start) / 1_000_000));
    }
}
