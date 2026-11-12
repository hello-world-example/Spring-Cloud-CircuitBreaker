package com.kai.learn.cbdemo.l1.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import com.kai.learn.cbdemo.common.Timer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观察用的接口。
 * <ul>
 *   <li>{@code /demo/call}：调用一次</li>
 *   <li>{@code /demo/burst?n=15}：连续调用 n 次，并打印每次调用后熔断器的状态，<b>最适合观察状态切换</b></li>
 *   <li>{@code /demo/state}：查看熔断器当前的状态和统计指标</li>
 * </ul>
 *
 * 【对比】业务代码（{@link ProductService}）依赖的是 Spring Cloud 的抽象接口；这里为了<b>观察内部状态</b>，
 * 直接注入了 Resilience4j 的 {@link CircuitBreakerRegistry}。Spring Cloud 的抽象层没有提供查询状态的 API，
 * 因为不同实现的状态模型不同（见 L2-06）。
 */
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final ProductService productService;
    private final CircuitBreakerRegistry registry;

    @GetMapping("/demo/call")
    public ApiResult<Map<String, Object>> call() {
        return productService.getProduct("l1-01");
    }

    @GetMapping("/demo/burst")
    public List<CallRecord> burst(@RequestParam(defaultValue = "15") int n,
                                  @RequestParam(defaultValue = "100") long intervalMs) throws InterruptedException {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> productService.getProduct("l1-01"));
            ApiResult<Map<String, Object>> r = timed.value();
            records.add(new CallRecord(i, r.source(), r.message(), state().name(), timed.costMs(),
                    Thread.currentThread().getName()));
            Thread.sleep(intervalMs);
        }
        return records;
    }

    @GetMapping("/demo/state")
    public Map<String, Object> state(@RequestParam(defaultValue = "downstream") String id) {
        CircuitBreaker cb = registry.circuitBreaker(id);
        CircuitBreaker.Metrics m = cb.getMetrics();
        return Map.of(
                "state", cb.getState(),
                // 失败率：最少调用数（minimumNumberOfCalls）达到之前返回 -1，表示「样本不足，不做判断」
                "failureRate", m.getFailureRate(),
                "bufferedCalls", m.getNumberOfBufferedCalls(),
                "failedCalls", m.getNumberOfFailedCalls(),
                // OPEN 状态下被拒绝的调用次数
                "notPermittedCalls", m.getNumberOfNotPermittedCalls());
    }

    private CircuitBreaker.State state() {
        return registry.circuitBreaker("downstream").getState();
    }
}
