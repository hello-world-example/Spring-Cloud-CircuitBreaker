package com.kai.learn.cbdemo.l1.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import com.kai.learn.cbdemo.common.Timer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【是什么】查看每个 id <b>最终生效</b>的配置，并用 burst 验证配置效果。
 *
 * <p>本模块准备了 5 个 id，分别对应 5 种配置来源：
 * <pre>
 *   id             group    配置来源（预期）
 *   plain          -        Customizer.configureDefault（failureRate 60）
 *   customized     -        Customizer.configure（failureRate 30）
 *   yaml-instance  -        YAML instances 与 Customizer.configure 都配置了 → 谁生效？
 *   order-api      strict   YAML configs.strict（failureRate 20）
 *   slow-call      -        YAML instances：慢调用比例熔断
 * </pre>
 */
@RestController
public class ConfigDemoController {

    private static final Map<String, String> IDS = new LinkedHashMap<>();

    static {
        IDS.put("plain", "");
        IDS.put("customized", "");
        IDS.put("yaml-instance", "");
        IDS.put("order-api", "strict");
        IDS.put("slow-call", "");
    }

    private final CircuitBreakerFactory<?, ?> factory;
    private final CircuitBreakerRegistry registry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final DownstreamApi api;

    public ConfigDemoController(CircuitBreakerFactory<?, ?> factory, CircuitBreakerRegistry registry,
                                TimeLimiterRegistry timeLimiterRegistry, DownstreamApi api) {
        this.factory = factory;
        this.registry = registry;
        this.timeLimiterRegistry = timeLimiterRegistry;
        this.api = api;
    }

    /** 所有 id 最终生效的配置 */
    @GetMapping("/demo/config")
    public Map<String, Object> allConfigs() {
        Map<String, Object> result = new LinkedHashMap<>();
        IDS.forEach((id, group) -> result.put(id, effectiveConfig(id)));
        return result;
    }

    /** 用指定 id 连续调用下游 n 次 */
    @GetMapping("/demo/burst/{id}")
    public List<CallRecord> burst(@PathVariable String id,
                                  @RequestParam(defaultValue = "12") int n,
                                  @RequestParam(defaultValue = "100") long intervalMs) throws InterruptedException {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> breaker(id).run(
                    () -> api.data("l1-02-" + id),
                    t -> ApiResult.fallback(Errors.describe(t))));
            records.add(new CallRecord(i, timed.value().source(), timed.value().message(),
                    registry.circuitBreaker(id).getState().name(), timed.costMs(), Thread.currentThread().getName()));
            Thread.sleep(intervalMs);
        }
        return records;
    }

    /**
     * 【原理】create(id) 与 create(id, group) 的区别：
     * <ul>
     *   <li>create(id)：组名就是 id 本身</li>
     *   <li>create(id, group)：配置查找顺序中多了 configs.&lt;group&gt; 这一级；同组的熔断器共用一个执行线程池；
     *       指标会带上 group 标签，便于在监控中按服务汇总</li>
     * </ul>
     */
    private org.springframework.cloud.client.circuitbreaker.CircuitBreaker breaker(String id) {
        String group = IDS.getOrDefault(id, "");
        return group.isEmpty() ? factory.create(id) : factory.create(id, group);
    }

    private Map<String, Object> effectiveConfig(String id) {
        // 熔断器实例在第一次 run() 时才会注册到 registry，先空跑一次（会被记为一次成功调用）
        breaker(id).run(() -> "warm-up");
        CircuitBreaker cb = registry.circuitBreaker(id);
        CircuitBreakerConfig c = cb.getCircuitBreakerConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", IDS.get(id));
        m.put("failureRateThreshold", c.getFailureRateThreshold());
        m.put("slidingWindowType", c.getSlidingWindowType());
        m.put("slidingWindowSize", c.getSlidingWindowSize());
        m.put("minimumNumberOfCalls", c.getMinimumNumberOfCalls());
        m.put("waitDurationInOpenStateMs", c.getWaitIntervalFunctionInOpenState().apply(1));
        m.put("slowCallDurationThresholdMs", c.getSlowCallDurationThreshold().toMillis());
        m.put("slowCallRateThreshold", c.getSlowCallRateThreshold());
        m.put("timeLimiterTimeoutMs", timeLimiterRegistry.find(id)
                .map(tl -> tl.getTimeLimiterConfig().getTimeoutDuration().toMillis())
                .orElse(-1L));
        return m;
    }
}
