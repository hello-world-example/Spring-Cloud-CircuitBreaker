package com.kai.learn.cbdemo.l3.source;

import java.util.concurrent.Executors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 修复上下文丢失：把 Factory 的两个线程池都替换为 {@link ContextPropagatingExecutorService}。
 * 只有设置了 demo.propagate-context=true 时才生效，这样可以在测试中对比修复前后的差异。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "demo.propagate-context", havingValue = "true")
public class ContextPropagationConfig {

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> contextPropagationCustomizer() {
        return factory -> {
            // create(id) 使用的线程池
            factory.configureExecutorService(
                    new ContextPropagatingExecutorService(Executors.newCachedThreadPool()));
            // create(id, group) 使用的线程池：每个 group 一个
            factory.configureGroupExecutorService(group ->
                    new ContextPropagatingExecutorService(Executors.newCachedThreadPool()));
        };
    }
}
