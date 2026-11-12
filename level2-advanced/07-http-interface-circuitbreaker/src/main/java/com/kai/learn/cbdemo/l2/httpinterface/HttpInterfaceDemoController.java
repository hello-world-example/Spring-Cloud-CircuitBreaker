package com.kai.learn.cbdemo.l2.httpinterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import com.kai.learn.cbdemo.common.Timer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 观察接口：业务代码直接注入 {@link DownstreamHttpApi} 使用，看不到熔断代码。
 */
@RestController
public class HttpInterfaceDemoController {

    private final DownstreamHttpApi api;
    private final CircuitBreakerRegistry registry;

    public HttpInterfaceDemoController(DownstreamHttpApi api, CircuitBreakerRegistry registry) {
        this.api = api;
        this.registry = registry;
    }

    @GetMapping("/demo/burst")
    public List<CallRecord> burst(@RequestParam(defaultValue = "10") int n,
                                  @RequestParam(defaultValue = "100") long intervalMs) throws InterruptedException {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> api.data("l2-07"));
            records.add(new CallRecord(i, timed.value().source(), timed.value().message(),
                    // 熔断器 id 就是组名
                    registry.circuitBreaker("downstream").getState().name(),
                    timed.costMs(), Thread.currentThread().getName()));
            Thread.sleep(intervalMs);
        }
        return records;
    }

    @GetMapping("/demo/breakers")
    public List<String> breakers() {
        return registry.getAllCircuitBreakers().stream().map(cb -> cb.getName() + " = " + cb.getState()).toList();
    }
}
