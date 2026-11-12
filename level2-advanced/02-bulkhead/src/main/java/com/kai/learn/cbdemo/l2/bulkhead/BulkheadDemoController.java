package com.kai.learn.cbdemo.l2.bulkhead;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【是什么】同一时刻发起 concurrency 个请求，观察舱壁如何「放行一部分、排队一部分、拒绝其余的」。
 *
 * 【为什么】熔断解决的是「下游已经坏了」，舱壁解决的是「下游变慢时，不要让它占满我所有的线程」。
 * 名字来自船舱的隔板：一个舱进水，不会淹没整艘船。没有舱壁时，一个慢下游可以耗尽 Tomcat 的全部 200 个线程，
 * 导致调用其他健康下游的请求也无法处理（L4-03 会完整演示）。
 *
 * 【原理】两种实现（单独使用 Resilience4j 时）：
 * <pre>
 *                 线程池舱壁（ThreadPoolBulkhead）        信号量舱壁（SemaphoreBulkhead）
 *   隔离方式      独立的线程池 + 有界队列                  计数信号量，最多 N 个并发
 *   执行线程      舱壁自己的线程                           调用方线程
 *   超出容量      先排队，队列满了再拒绝                   等待 max-wait-duration，拿不到许可就拒绝
 *   开销          线程切换、ThreadLocal 丢失               几乎没有
 *   适用          阻塞 I/O，需要和调用方线程彻底隔离       响应式 / 虚拟线程 / 很快的调用
 * </pre>
 *
 * 【坑】经过 Spring Cloud 包装之后，「执行线程」这一行不再成立。实测 supplierThread 始终是 {@code pool-2-thread-N}，
 * 也就是 Factory 内部的 CachedThreadPool（为 TimeLimiter 准备的），而不是舱壁的线程。源码
 * {@code Resilience4JCircuitBreaker#run} 中的嵌套关系是：
 * <pre>
 *   CircuitBreaker( Bulkhead( TimeLimiter( executorService.submit(业务代码) ) ) )
 * </pre>
 * 线程池舱壁的线程只是「占着名额等待」Factory 线程池的结果，<b>一次调用占用两个线程</b>。
 * 如果设置了 disable-thread-pool=true，业务代码才会运行在舱壁线程（线程池舱壁）或调用方线程（信号量舱壁）中，
 * 但同时 TimeLimiter 也会失效。详见 L3-03。
 *
 * 【原理】Spring Cloud 如何为某个 id 选择舱壁类型（源码 {@code Resilience4jBulkheadProvider#useSemaphoreBulkhead}）：
 * <ol>
 *   <li>thread-pool-bulkhead 中有这个 id 的配置 → 线程池舱壁</li>
 *   <li>bulkhead 中有这个 id 的配置，或者 enableSemaphoreDefaultBulkhead=true → 信号量舱壁</li>
 *   <li>否则 → 线程池舱壁（默认）</li>
 * </ol>
 * 所以在同一个应用中，可以为不同的 id 配置不同类型的舱壁。
 *
 * 【坑】舱壁的 key 是 <b>group 名</b>，而不是 id（源码 {@code Resilience4JCircuitBreaker#run} 中
 * {@code bulkheadProvider.decorateCallable(this.groupName, ...)}）。用 create(id) 时 group 就是 id，看不出区别；
 * 但用 create(id, group) 时，同组的所有 id 共用一个舱壁。
 */
@RestController
@RequiredArgsConstructor
public class BulkheadDemoController {

    private final CircuitBreakerFactory<?, ?> factory;
    private final DownstreamApi api;

    record Attempt(int seq, String source, String message, long costMs, String supplierThread, String callerThread) {
    }

    /**
     * @param id          tp-bulkhead（线程池舱壁）或 sem-bulkhead（信号量舱壁）
     * @param concurrency 同时发起的请求数
     * @param delayMs     下游每个请求的耗时
     */
    @GetMapping("/demo/bulkhead/{id}")
    public Map<String, Object> bulkhead(@PathVariable String id,
                                        @RequestParam(defaultValue = "8") int concurrency,
                                        @RequestParam(defaultValue = "1000") long delayMs) throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Attempt>> futures = new ArrayList<>();
        // 用虚拟线程模拟 concurrency 个并发用户。它们会在 startGate 处等待，然后同时出发
        try (ExecutorService users = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= concurrency; i++) {
                int seq = i;
                futures.add(users.submit(() -> {
                    startGate.await();
                    String callerThread = Thread.currentThread().toString();
                    String[] supplierThread = {"-"};
                    long start = System.nanoTime();
                    ApiResult<Map<String, Object>> r = factory.create(id).run(() -> {
                        supplierThread[0] = Thread.currentThread().getName();
                        return api.slow(delayMs);
                    }, t -> ApiResult.fallback(Errors.describe(t)));
                    return new Attempt(seq, r.source(), r.message(), (System.nanoTime() - start) / 1_000_000,
                            supplierThread[0], callerThread);
                }));
            }
            startGate.countDown();
        }
        List<Attempt> attempts = new ArrayList<>();
        for (Future<Attempt> f : futures) {
            attempts.add(f.get());
        }
        attempts.sort(Comparator.comparingLong(Attempt::costMs));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", attempts.stream().filter(a -> a.source().equals(ApiResult.SOURCE_DOWNSTREAM)).count());
        result.put("rejected", attempts.stream().filter(a -> a.message().contains("BulkheadFullException")).count());
        result.put("attempts", attempts);
        return result;
    }
}
