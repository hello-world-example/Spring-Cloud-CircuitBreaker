package com.kai.learn.cbdemo.l2.switchimpl.business;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.DownstreamApi;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * 观察接口，三个 app 完全相同。
 * <ul>
 *   <li>{@code /demo/impl}：当前使用的 CircuitBreakerFactory 实现类</li>
 *   <li>{@code /demo/burst?n=10}：连续调用 n 次。因为不同实现的状态模型不同，这里不显示状态，
 *       而是显示每次 run() 实际调用下游的次数（downstreamCalls）：0 = 被熔断拦截，&gt;1 = 发生了重试</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class SwitchDemoController {

    private final InventoryService service;

    @GetMapping("/demo/impl")
    public Map<String, String> impl() {
        return Map.of("factory", service.implementation());
    }

    @GetMapping("/demo/burst")
    public List<Map<String, Object>> burst(@RequestParam(defaultValue = "10") int n,
                                           @RequestParam(defaultValue = "200") long intervalMs) throws InterruptedException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            long start = System.nanoTime();
            InventoryService.Result r = service.query();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("seq", i);
            row.put("source", r.result().source());
            row.put("downstreamCalls", r.downstreamCalls());
            row.put("costMs", (System.nanoTime() - start) / 1_000_000);
            row.put("message", r.result().message());
            rows.add(row);
            Thread.sleep(intervalMs);
        }
        return rows;
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientConfig {
        @Bean
        DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
            return new DownstreamApi(builder.baseUrl(baseUrl).build());
        }
    }
}
