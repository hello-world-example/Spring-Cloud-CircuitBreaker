package com.kai.learn.cbdemo.l2.feign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import com.kai.learn.cbdemo.common.Timer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观察接口：业务代码中直接调用 Feign 接口，看不到任何熔断代码。
 */
@RestController
public class FeignDemoController {

    private final DownstreamFeignClient client;
    private final CircuitBreakerRegistry registry;

    public FeignDemoController(DownstreamFeignClient client, CircuitBreakerRegistry registry) {
        this.client = client;
        this.registry = registry;
    }

    /** 所有已经创建的熔断器：名称、状态、生效的失败率阈值。先调用几次接口再看 */
    @GetMapping("/demo/breakers")
    public Map<String, Object> breakers() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (CircuitBreaker cb : registry.getAllCircuitBreakers()) {
            result.put(cb.getName(), Map.of(
                    "state", cb.getState(),
                    "failureRateThreshold", cb.getCircuitBreakerConfig().getFailureRateThreshold(),
                    "tags", cb.getTags()));
        }
        return result;
    }

    @GetMapping("/demo/burst")
    public List<CallRecord> burst(@RequestParam(defaultValue = "12") int n,
                                  @RequestParam(defaultValue = "100") long intervalMs) throws InterruptedException {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> client.data("l2-03"));
            records.add(new CallRecord(i, timed.value().source(), timed.value().message(), dataBreakerState(),
                    timed.costMs(), Thread.currentThread().getName()));
            Thread.sleep(intervalMs);
        }
        return records;
    }

    @GetMapping("/demo/slow")
    public Map<String, Object> slow(@RequestParam(defaultValue = "1500") long delayMs) {
        Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> client.slow(delayMs));
        return Map.of("result", timed.value(), "costMs", timed.costMs());
    }

    private String dataBreakerState() {
        return registry.getAllCircuitBreakers().stream()
                .filter(cb -> cb.getName().contains("data"))
                .findFirst()
                .map(cb -> cb.getState().name())
                .orElse("-");
    }
}
