package com.kai.learn.cbdemo.l2.switchimpl.business;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * 【是什么】只依赖 Spring Cloud 抽象接口的业务代码，三个 app 共用。
 *
 * 【为什么】这正是 Spring Cloud CircuitBreaker 存在的意义：业务代码与具体的熔断实现解耦，
 * 换实现只需要换 starter 和配置。
 *
 * 【坑】抽象只统一了「怎么调用」（run(supplier, fallback)），<b>没有统一「行为」</b>：
 * 三种实现对「什么时候打开」「要不要重试」「怎么恢复」的理解完全不同。换实现之后，同样的 burst 会得到不同的结果，
 * 运行三个 app 对比一下就知道了。
 *
 * 为了看清「重试」，这里用一个计数器记录每次 run() 实际调用了下游多少次。
 */
@Service
public class InventoryService {

    private final CircuitBreakerFactory<?, ?> factory;
    private final DownstreamApi api;
    private final AtomicInteger downstreamCalls = new AtomicInteger();

    public InventoryService(CircuitBreakerFactory<?, ?> factory, DownstreamApi api) {
        this.factory = factory;
        this.api = api;
    }

    public record Result(ApiResult<Map<String, Object>> result, int downstreamCalls) {
    }

    public Result query() {
        downstreamCalls.set(0);
        ApiResult<Map<String, Object>> r = factory.create("inventory").run(() -> {
            downstreamCalls.incrementAndGet();
            return api.data("l2-06");
        }, t -> ApiResult.fallback(Errors.describe(t)));
        return new Result(r, downstreamCalls.get());
    }

    public String implementation() {
        return factory.getClass().getName();
    }
}
