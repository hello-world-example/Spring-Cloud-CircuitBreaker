package com.kai.learn.cbdemo.l2.switchimpl.frameworkretry;

import java.time.Duration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.retry.FrameworkRetryConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.retry.RetryPolicy;

/**
 * 实现三：Framework Retry（Spring Cloud 2025.1 新增）。
 *
 * 【原理】源码 {@code FrameworkRetryCircuitBreaker#run} 与 {@code CircuitBreakerRetryPolicy}：
 * <ol>
 *   <li>熔断打开且还没过 openTimeout → 直接降级</li>
 *   <li>否则用 Spring Framework 7 的 {@code RetryTemplate} 执行，<b>在一次 run() 中循环重试</b>（无状态重试）</li>
 *   <li>重试全部失败 → recordFailure()：CLOSED 状态下<b>第一次</b>失败就直接打开熔断</li>
 *   <li>打开后过了 openTimeout → HALF_OPEN，放行一次：成功则关闭，失败则重新打开</li>
 *   <li>距离上次失败超过 resetTimeout → 也会重置为 CLOSED</li>
 * </ol>
 * 【对比】与 Resilience4j 最大的不同：没有滑动窗口、没有失败率，「一次 run() 的重试全部失败」就熔断。
 * 它更像是「重试 + 简单的冷却期」，适合调用量小、对下游故障要求快速反应的场景。
 *
 * 【坑】默认 openTimeout = 20s，resetTimeout = 5s，而且 resetTimeout 是按「距离最后一次失败」计算的。
 * 两个时间的交互不太直观，务必在测试环境中验证你的配置。
 */
@SpringBootApplication(scanBasePackages = "com.kai.learn.cbdemo.l2.switchimpl")
public class FrameworkRetryApp {

    public static void main(String[] args) {
        SpringApplication.run(FrameworkRetryApp.class, args);
    }

    @Bean
    Customizer<FrameworkRetryCircuitBreakerFactory> customizer() {
        return factory -> factory.configureDefault(id -> new FrameworkRetryConfigBuilder(id)
                // 一次 run() 中最多重试 2 次（共 3 次调用），每次间隔 100ms
                .retryPolicy(RetryPolicy.builder().maxRetries(2).delay(Duration.ofMillis(100)).build())
                // 打开 5 秒后进入 HALF_OPEN
                .openTimeout(Duration.ofSeconds(5))
                // 距离最后一次失败 10 秒后重置
                .resetTimeout(Duration.ofSeconds(10))
                .build());
    }
}
