package com.kai.learn.cbdemo.l4.ops;

import java.util.LinkedHashMap;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产生调用，配合 {@link OpsController} 观察人工干预的效果。
 */
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final CircuitBreakerFactory<?, ?> factory;
    private final CircuitBreakerRegistry registry;
    private final DownstreamApi api;

    /** 连续调用 n 次，统计各种结果的次数 */
    @GetMapping("/demo/burst")
    public Map<String, Object> burst(@RequestParam(defaultValue = "10") int n) {
        int downstream = 0;
        Map<String, Integer> fallbackReasons = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            ApiResult<Map<String, Object>> r = factory.create("inventory").run(
                    () -> api.data("l4-04"), t -> ApiResult.fallback(Errors.unwrap(t).getClass().getSimpleName()));
            if (ApiResult.SOURCE_DOWNSTREAM.equals(r.source())) {
                downstream++;
            } else {
                fallbackReasons.merge(r.message(), 1, Integer::sum);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", registry.circuitBreaker("inventory").getState());
        m.put("downstream", downstream);
        m.put("fallback", fallbackReasons);
        return m;
    }
}
