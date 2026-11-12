package com.kai.learn.cbdemo.l4.layered;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【是什么】对比实验：用一个 20 线程的线程池模拟容器（Tomcat）的工作线程，同时涌入 requests 个页面请求，
 * 分别在「无保护」和「有保护」两种模式下统计总耗时、平均耗时和 P95。
 *
 * 【为什么】用固定大小的线程池而不是直接压测 Tomcat，是为了让「线程被占满」这个现象在本机上稳定复现，
 * 不受机器性能和虚拟线程配置的影响。
 */
@RestController
@RequiredArgsConstructor
public class LayeredDemoController {

    private static final int CONTAINER_THREADS = 20;

    private final ProductPageService service;

    @GetMapping("/demo/page")
    public ProductPageService.Page page(@RequestParam(defaultValue = "2000") long recommendDelayMs) {
        return service.buildProtected(recommendDelayMs);
    }

    @GetMapping("/demo/compare")
    public Map<String, Object> compare(@RequestParam(defaultValue = "60") int requests,
                                       @RequestParam(defaultValue = "2000") long recommendDelayMs) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unprotected", load(requests, () -> service.buildUnprotected(recommendDelayMs)));
        result.put("protected", load(requests, () -> service.buildProtected(recommendDelayMs)));
        return result;
    }

    private Map<String, Object> load(int requests, java.util.function.Supplier<ProductPageService.Page> page)
            throws Exception {
        ExecutorService container = Executors.newFixedThreadPool(CONTAINER_THREADS);
        long start = System.nanoTime();
        List<Future<long[]>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            // 记录的是「从请求到达到响应完成」的时间，包括在线程池中排队的时间，这才是用户感受到的耗时
            long arrived = System.nanoTime();
            futures.add(container.submit(() -> {
                ProductPageService.Page p = page.get();
                return new long[]{(System.nanoTime() - arrived) / 1_000_000, p.recommendDegraded() ? 1 : 0,
                        p.inventoryOk() ? 1 : 0};
            }));
        }
        List<Long> latencies = new ArrayList<>();
        int degraded = 0;
        int inventoryOk = 0;
        for (Future<long[]> f : futures) {
            long[] r = f.get();
            latencies.add(r[0]);
            degraded += (int) r[1];
            inventoryOk += (int) r[2];
        }
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        container.shutdown();

        Collections.sort(latencies);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalMs", totalMs);
        m.put("avgMs", latencies.stream().mapToLong(Long::longValue).average().orElse(0));
        m.put("p95Ms", latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1));
        m.put("inventoryOk", inventoryOk);
        m.put("recommendDegraded", degraded);
        return m;
    }
}
