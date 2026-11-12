package com.kai.learn.cbdemo.l2.switchimpl.springretry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.springretry.SpringRetryCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.springretry.SpringRetryConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * 实现二：Spring Retry。
 *
 * 【原理】基于<b>有状态重试</b>（stateful retry）：源码 {@code SpringRetryCircuitBreaker#run} 使用
 * {@code RetryTemplate.execute(callback, recovery, new DefaultRetryState(id, ...))}。
 * 「有状态」的意思是：重试的计数保存在以 id 为 key 的 RetryContext 中，<b>跨越多次 run() 调用</b>，
 * 而不是在一次 run() 中循环重试。
 * <ul>
 *   <li>{@link SimpleRetryPolicy}(3)：累计失败 3 次后，「重试耗尽」</li>
 *   <li>{@link CircuitBreakerRetryPolicy}：重试耗尽发生在 openTimeout 内 → 打开熔断；
 *       打开后经过 resetTimeout → 关闭熔断</li>
 * </ul>
 * 没有滑动窗口，也没有失败率的概念。
 *
 * 【坑】实测（Spring Cloud CircuitBreaker 5.0.3 + Spring Retry 2.0.13，本模块的配置）：下游持续报错时，
 * <b>熔断器始终没有打开</b>，20 次调用全部打到了下游。原因在 Spring Retry 的 {@code RetryTemplate#doExecute}：
 * <ol>
 *   <li>CircuitBreakerRetryPolicy 会给 RetryContext 设置 GLOBAL_STATE 属性</li>
 *   <li>有状态重试 + GLOBAL_STATE → 每次失败后立即 break，进入 handleRetryExhausted 调用降级</li>
 *   <li>handleRetryExhausted 中的 {@code retryContextCache.remove(key)} 把这个 id 的 RetryContext 删掉了</li>
 *   <li>下一次 run() 拿到的是一个新的 RetryContext，失败计数从 0 开始，永远到不了 3</li>
 * </ol>
 * Spring Retry 自身的 {@code @CircuitBreaker} 注解之所以能用，是因为它的 rollback 分类器会把异常重新抛出，
 * 不进入降级。结论：选择实现时，<b>一定要用失败场景实测</b>，不能只看抽象接口。
 */
@SpringBootApplication(scanBasePackages = "com.kai.learn.cbdemo.l2.switchimpl")
public class SpringRetryApp {

    public static void main(String[] args) {
        SpringApplication.run(SpringRetryApp.class, args);
    }

    @Bean
    Customizer<SpringRetryCircuitBreakerFactory> customizer() {
        return factory -> factory.configureDefault(id -> {
            CircuitBreakerRetryPolicy policy = new CircuitBreakerRetryPolicy(new SimpleRetryPolicy(3));
            // 在 5 秒内重试耗尽 → 打开熔断
            policy.setOpenTimeout(5_000);
            // 打开 10 秒后 → 关闭熔断
            policy.setResetTimeout(10_000);
            // 【动手】加上 .stateClassifier(t -> true)（让有状态重试把异常重新抛出），再运行 burst：
            // 异常会直接从 run() 抛出，降级函数不再被调用，接口返回 500
            return new SpringRetryConfigBuilder(id).retryPolicy(policy).build();
        });
    }
}
