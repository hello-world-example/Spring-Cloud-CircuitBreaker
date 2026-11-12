package com.kai.learn.cbdemo.l1.config;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 【是什么】用代码（{@link Customizer}）配置熔断器，这是 YAML 之外的第二种配置方式。
 *
 * 【为什么】有些配置 YAML 表达不了，比如自定义「什么样的结果算失败」的 Predicate、动态计算的参数、
 * 需要引用其他 Bean 的逻辑，这时就要用代码配置。
 *
 * 【原理】{@code Customizer<Resilience4JCircuitBreakerFactory>} 是 Spring Cloud 提供的扩展点：
 * 自动配置在创建 Factory 后，会把容器中所有这个类型的 Bean 依次应用到 Factory 上（源码：
 * {@code Resilience4JAutoConfiguration#resilience4jCircuitBreakerFactory}）。
 *
 * 【坑】配置优先级（从源码 {@code Resilience4JCircuitBreakerFactory#create} 和 {@code Resilience4JCircuitBreaker#run} 总结得出）：
 * <pre>
 *   1. YAML instances.&lt;id&gt;  —— 启动时就创建好了熔断器实例，run() 时 registry.circuitBreaker(id, config)
 *                               直接返回已有的实例，代码中算出来的 config 被忽略。所以它的优先级最高
 *   2. YAML configs.&lt;id&gt;    —— 和 id 同名的「命名配置」
 *   3. YAML configs.&lt;group&gt; —— create(id, group) 时，按组名查找命名配置
 *   4. Customizer: factory.configure(..., id)  —— 针对指定 id 的代码配置
 *   5. Customizer: factory.configureDefault(...) —— 代码中的全局默认配置
 *      （没有配置 configureDefault 时，使用 YAML 中的 configs.default）
 * </pre>
 * 官方文档只写了一句「Property 配置优先于 Customizer」，具体的覆盖关系需要运行 {@code /demo/config} 亲自验证。
 */
@Configuration(proxyBeanMethods = false)
public class CircuitBreakerCustomizerConfig {

    /**
     * 针对指定 id 的代码配置。
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> specificCustomizer() {
        return factory -> factory.configure(builder -> builder
                        .circuitBreakerConfig(CircuitBreakerConfig.custom()
                                .slidingWindowSize(10)
                                .minimumNumberOfCalls(5)
                                .failureRateThreshold(30)
                                .waitDurationInOpenState(Duration.ofSeconds(5))
                                .build())
                        .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build()),
                // 两个 id 使用同一份配置。
                // 【动手】yaml-instance 在 YAML 的 instances 中也配置了一份，看看最终是哪一份生效？
                "customized", "yaml-instance");
    }

    /**
     * 代码中的全局默认配置：所有「YAML 中没有配置、也没有通过 configure 指定」的 id 都使用它。
     *
     * 【动手】把这个 Bean 注释掉再访问 /demo/config，plain 的 failureRateThreshold 会变回 YAML configs.default 中的 50。
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(60)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build())
                .build());
    }
}
