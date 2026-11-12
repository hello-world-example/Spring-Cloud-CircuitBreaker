package com.kai.learn.cbdemo.l2.switchimpl.resilience4j;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;

/**
 * 实现一：Resilience4j。
 *
 * 【原理】基于<b>滑动窗口的失败率</b>：窗口内失败率 ≥ 阈值才打开。偶发的单次失败不会导致熔断，<b>不做重试</b>。
 * 每次 run() 最多调用下游 1 次。
 */
@SpringBootApplication(scanBasePackages = "com.kai.learn.cbdemo.l2.switchimpl")
public class Resilience4jApp {

    public static void main(String[] args) {
        SpringApplication.run(Resilience4jApp.class, args);
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> customizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build())
                .build());
    }
}
