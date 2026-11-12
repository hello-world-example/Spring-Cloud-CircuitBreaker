package com.kai.learn.cbdemo.l4.observability;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【是什么】持续产生流量：每 100ms 调用一次库存服务（inventory）和推荐服务（recommend）。
 *
 * 【为什么】监控看板需要持续的数据。两个熔断器使用不同的 group，可以在看板上按服务筛选、对比。
 *
 * <p>开关：{@code POST /traffic?enabled=false} 暂停流量。
 */
@Component
@RestController
@RequiredArgsConstructor
public class TrafficGenerator {

    private final CircuitBreakerFactory<?, ?> factory;
    private final DownstreamApi api;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @Scheduled(fixedRate = 100)
    public void tick() {
        if (!enabled.get()) {
            return;
        }
        // 库存：跟随下游的故障模式（/chaos）变化
        factory.create("inventory", "inventory-service").run(
                () -> api.data("l4-02"), t -> ApiResult.<Map<String, Object>>fallback(t.getClass().getSimpleName()));
        // 推荐：固定 50ms 的慢接口，作为看板上的「健康」对照组
        factory.create("recommend", "recommend-service").run(
                () -> api.slow(50), t -> ApiResult.<Map<String, Object>>fallback(t.getClass().getSimpleName()));
    }

    @PostMapping("/traffic")
    public Map<String, Boolean> toggle(@RequestParam boolean enabled) {
        this.enabled.set(enabled);
        return Map.of("enabled", enabled);
    }
}
