package com.kai.learn.cbdemo.l2.metrics;

import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产生一些调用，然后通过 {@code /demo/events}（自己记录的事件）和 {@code /actuator/prometheus}（标准指标）观察。
 */
@RestController
@RequiredArgsConstructor
public class MetricsDemoController {

    private final CircuitBreakerFactory<?, ?> factory;
    private final DownstreamApi api;
    private final CircuitBreakerEventRecorder recorder;

    @GetMapping("/demo/burst")
    public Map<String, Long> burst(@RequestParam(defaultValue = "12") int n) {
        long ok = 0;
        long fallback = 0;
        for (int i = 0; i < n; i++) {
            // create(id, group)：指标中会带上 group 标签，便于在 Grafana 中按服务汇总
            ApiResult<Map<String, Object>> r = factory.create("inventory", "inventory-service").run(
                    () -> api.data("l2-05"), t -> ApiResult.fallback(Errors.describe(t)));
            if (ApiResult.SOURCE_DOWNSTREAM.equals(r.source())) {
                ok++;
            } else {
                fallback++;
            }
        }
        return Map.of("downstream", ok, "fallback", fallback);
    }

    @GetMapping("/demo/events")
    public List<CircuitBreakerEventRecorder.EventView> events() {
        return recorder.recent();
    }
}
