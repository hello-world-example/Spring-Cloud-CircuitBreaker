package com.kai.learn.cbdemo.l2.reactive;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 【是什么】响应式版本的熔断：{@code ReactiveCircuitBreaker.run(Mono, fallback)}。
 *
 * 【为什么】WebFlux 应用中不能使用阻塞式的 {@code CircuitBreakerFactory}：它会把调用提交到线程池并阻塞等待 Future，
 * 这会破坏响应式的非阻塞模型。
 *
 * 【原理】响应式实现（源码 {@code ReactiveResilience4JCircuitBreaker#run}）只是给 Mono 加上了几个操作符：
 * <pre>
 *   toRun
 *     .transform(bulkhead)                        // 有舱壁时（响应式只支持信号量舱壁）
 *     .transform(CircuitBreakerOperator.of(cb))   // 订阅时检查是否允许调用，完成/出错时记录结果
 *     .timeout(timeoutDuration)                   // 超时由 Reactor 的定时器实现，没有额外的线程池
 *     .doOnError(TimeoutException.class, t -> cb.onError(...))
 *     .onErrorResume(fallback)
 * </pre>
 * 有意思的是 doOnError 这一行：timeout 在熔断操作符的<b>外层</b>，超时时熔断操作符只收到一个「取消」信号，
 * 并不知道发生了错误，所以源码中要再手动调用一次 cb.onError()，把超时计为失败。
 * <b>没有线程切换</b>：调用运行在 Netty 的事件循环线程上，ThreadLocal 问题换成了 Reactor Context 的问题。
 *
 * 【对比】阻塞版本（L1-03）中，Supplier 运行在 Factory 内部的线程池中，超时依靠 Future.get(timeout) 实现。
 *
 * 【坑】必须把「整个调用」包在 Mono 中再交给 run()。如果在 run() 外面就调用了 block() 或提前触发了请求，
 * 熔断器就保护不到它。另外，Mono 是惰性的：run() 返回的 Mono 不被订阅，就什么都不会发生。
 */
@Service
public class ReactiveProductService {

    private static final ParameterizedTypeReference<ApiResult<Map<String, Object>>> TYPE = new ParameterizedTypeReference<>() {
    };

    private final WebClient webClient;
    private final ReactiveCircuitBreakerFactory<?, ?> factory;

    public ReactiveProductService(WebClient.Builder builder, ReactiveCircuitBreakerFactory<?, ?> factory,
                                  @Value("${downstream.base-url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.factory = factory;
    }

    public Mono<ApiResult<Map<String, Object>>> getProduct() {
        Mono<ApiResult<Map<String, Object>>> call = webClient.get()
                .uri("/api/data?caller=l2-01")
                .retrieve()
                .bodyToMono(TYPE)
                // 记录实际处理响应的线程（Netty 的事件循环线程）
                .map(r -> withThread(r, Thread.currentThread().getName()));

        return factory.create("reactive").run(call,
                t -> Mono.just(ApiResult.fallback(Errors.describe(t))));
    }

    public Mono<ApiResult<Map<String, Object>>> slow(long delayMs) {
        Mono<ApiResult<Map<String, Object>>> call = webClient.get()
                .uri("/api/slow?delayMs={d}", delayMs)
                .retrieve()
                .bodyToMono(TYPE);
        return factory.create("reactive-timeout").run(call,
                t -> Mono.just(ApiResult.fallback(Errors.describe(t))));
    }

    private static ApiResult<Map<String, Object>> withThread(ApiResult<Map<String, Object>> r, String thread) {
        Map<String, Object> data = new LinkedHashMap<>(r.data());
        data.put("handledBy", thread);
        return new ApiResult<>(r.success(), r.source(), r.message(), data, r.time());
    }
}
