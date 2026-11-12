package com.kai.learn.cbdemo.l4.ops;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【是什么】熔断器的运维接口。
 *
 * 【为什么】自动熔断不能覆盖所有情况，线上经常需要人工干预：
 * <ul>
 *   <li><b>FORCED_OPEN（强制熔断）</b>：下游正在发布或已知故障，主动切断流量，避免一边等待超时一边打日志</li>
 *   <li><b>DISABLED（关闭熔断）</b>：熔断器误判（比如阈值配得太敏感），先关掉保证业务可用，再调参</li>
 *   <li><b>METRICS_ONLY（只统计不拦截）</b>：新接入熔断时先「观察」一段时间，根据真实的失败率再决定阈值</li>
 *   <li><b>动态调参</b>：大促前临时放宽阈值，或者故障时收紧</li>
 * </ul>
 *
 * 【原理】Resilience4j 的状态机除了 CLOSED / OPEN / HALF_OPEN 之外，还有三个「特殊状态」：
 * <pre>
 *   FORCED_OPEN    拒绝所有调用，不会自动恢复
 *   DISABLED       放行所有调用，不统计
 *   METRICS_ONLY   放行所有调用，只统计（会发布事件和指标），不会打开
 * </pre>
 * 进入这三个状态后，只能通过人工调用 transitionToClosedState() / reset() 回到正常的状态机。
 *
 * 【原理】动态调参：Resilience4j 的熔断器配置是不可变的，只能<b>替换整个熔断器实例</b>
 * （{@code registry.replace(name, newCircuitBreaker)}）。Spring Cloud 的 Resilience4JCircuitBreaker 每次 run()
 * 都会通过 {@code registry.circuitBreaker(id, ...)} 重新获取实例，所以替换之后立即生效。
 *
 * 【坑】
 * <ul>
 *   <li>替换实例会丢失原来窗口中的统计数据，状态重置为 CLOSED</li>
 *   <li>挂在旧实例上的事件监听器不会转移到新实例上（L2-05 的 RegistryEventConsumer 会收到 EntryReplacedEvent，需要处理）</li>
 *   <li>这里的修改只作用于当前这个实例（Pod），多实例部署时要通过配置中心广播，或者逐个调用</li>
 *   <li>【生产】这些接口能直接影响线上流量，必须加上鉴权和审计日志，并且不要暴露到公网</li>
 * </ul>
 */
@RestController
@RequestMapping("/ops/breakers")
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);

    private final CircuitBreakerRegistry registry;

    public OpsController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return registry.getAllCircuitBreakers().stream().map(OpsController::view).toList();
    }

    /**
     * @param action force-open / disable / metrics-only / close / reset
     */
    @PostMapping("/{name}/{action}")
    public Map<String, Object> changeState(@PathVariable String name, @PathVariable String action) {
        CircuitBreaker cb = registry.circuitBreaker(name);
        CircuitBreaker.State before = cb.getState();
        switch (action) {
            case "force-open" -> cb.transitionToForcedOpenState();
            case "disable" -> cb.transitionToDisabledState();
            case "metrics-only" -> cb.transitionToMetricsOnlyState();
            case "close" -> cb.transitionToClosedState();
            // reset：回到 CLOSED，并清空统计数据
            case "reset" -> cb.reset();
            default -> throw new IllegalArgumentException("不支持的操作：" + action);
        }
        // 【生产】审计日志：谁、在什么时候、做了什么
        log.warn("【运维操作】熔断器 [{}]：{} → {}（操作：{}）", name, before, cb.getState(), action);
        return view(cb);
    }

    /**
     * 动态调整失败率阈值与 OPEN 持续时间。
     */
    @PostMapping("/{name}/config")
    public Map<String, Object> updateConfig(@PathVariable String name,
                                            @RequestParam float failureRateThreshold,
                                            @RequestParam(defaultValue = "5") long waitSeconds) {
        CircuitBreaker old = registry.circuitBreaker(name);
        CircuitBreakerConfig newConfig = CircuitBreakerConfig.from(old.getCircuitBreakerConfig())
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(waitSeconds))
                .build();
        // 保留原来的标签（比如 group），否则指标的标签会发生变化
        CircuitBreaker replacement = CircuitBreaker.of(name, newConfig, old.getTags());
        registry.replace(name, replacement);
        log.warn("【运维操作】熔断器 [{}] 的配置已更新：failureRateThreshold={}，waitDurationInOpenState={}s",
                name, failureRateThreshold, waitSeconds);
        return view(registry.circuitBreaker(name));
    }

    private static Map<String, Object> view(CircuitBreaker cb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", cb.getName());
        m.put("state", cb.getState());
        m.put("failureRate", cb.getMetrics().getFailureRate());
        m.put("bufferedCalls", cb.getMetrics().getNumberOfBufferedCalls());
        m.put("failureRateThreshold", cb.getCircuitBreakerConfig().getFailureRateThreshold());
        m.put("waitDurationInOpenStateMs", cb.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1));
        return m;
    }
}
