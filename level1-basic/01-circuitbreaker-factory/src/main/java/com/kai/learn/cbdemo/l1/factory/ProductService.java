package com.kai.learn.cbdemo.l1.factory;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * 【是什么】本模块的核心：用 Spring Cloud 的 {@link CircuitBreakerFactory} 给远程调用加上熔断和降级。
 *
 * 【为什么】下游故障时，如果调用方还在不停地发请求：
 * <ol>
 *   <li>调用方的线程都卡在等待超时上，线程池被耗尽，故障沿调用链向上蔓延，形成「雪崩」</li>
 *   <li>下游本来就已经扛不住了，持续的请求让它更难恢复</li>
 * </ol>
 * 熔断器统计最近一段时间的失败率，超过阈值就<b>直接拒绝请求</b>（OPEN），一段时间后放少量请求去探测（HALF_OPEN），
 * 探测成功再恢复（CLOSED）。
 *
 * 【原理】三个状态：
 * <pre>
 *            失败率 ≥ 阈值                    等待 waitDurationInOpenState 后的下一次调用
 *   CLOSED ───────────────▶ OPEN ───────────────────────────────────────────▶ HALF_OPEN
 *     ▲                       ▲                                                   │
 *     │                       └──────────── 探测请求失败率仍然 ≥ 阈值 ────────────┤
 *     └──────────────────────────────────── 探测请求成功 ─────────────────────────┘
 * </pre>
 * 详见 docs/01-state-machine.md。
 *
 * 【对比】Hystrix（已停止维护）也采用同样的状态机，但它强制使用线程池隔离；Resilience4j 是函数式的装饰器，
 * 隔离方式（线程池或信号量）可以自由组合。Sentinel 则多了「流控」的视角。见 docs/00-overview.md。
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final DownstreamClient client;
    private final CircuitBreaker circuitBreaker;

    public ProductService(DownstreamClient client, CircuitBreakerFactory<?, ?> factory) {
        this.client = client;
        // 【原理】create(id) 并不会立刻创建 Resilience4j 熔断器，只是拿到一个「包装器」。
        // 真正的状态机实例在第一次 run() 时才从 CircuitBreakerRegistry 中按 id 获取或创建，同一个 id 共享同一份状态。
        // 【坑】id 决定了状态共享的范围：所有使用 "downstream" 这个 id 的调用共享同一个失败率统计。
        // 通常按「下游服务」或「下游接口」来划分，不要按请求划分（比如把 userId 拼进去），否则 Registry 会无限增长。
        this.circuitBreaker = factory.create("downstream");
    }

    public ApiResult<Map<String, Object>> getProduct(String caller) {
        return circuitBreaker.run(
                // 第一个参数：要保护的调用
                () -> client.getData(caller),
                // 第二个参数：降级逻辑。这几种情况都会走到这里：
                //   1. 调用本身抛出异常（下游 5xx、连接失败……）
                //   2. 熔断器处于 OPEN 状态，直接抛出 CallNotPermittedException，此时根本不会去调用下游
                //   3. 超过 TimeLimiter 的超时时间（TimeoutException，见 L1-03）
                throwable -> {
                    // 【生产】降级时要打日志，但不要打印完整堆栈：OPEN 状态下每个请求都会走到这里，日志量会非常大
                    log.warn("降级：{}", Errors.describe(throwable));
                    return ApiResult.fallback(Errors.describe(throwable), Map.of("item", "默认商品（降级数据）"));
                });
    }
}
