package com.kai.learn.cbdemo.l3.source;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 【是什么】另一种「修复」：spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true。
 *
 * 【原理】源码 {@code Resilience4JCircuitBreakerFactory#create}：disableThreadPool 为 true 时，
 * 调用的是<b>不带 executorService</b> 的构造器，于是 {@code Resilience4JCircuitBreaker#run} 走到最后一个分支：
 * <pre>
 *   Supplier&lt;T&gt; decorator = CircuitBreaker.decorateSupplier(defaultCircuitBreaker, toRun);   // 没有 TimeLimiter！
 * </pre>
 *
 * 【坑】线程不再切换，上下文也就不会丢失，但<b>TimeLimiter 也随之悄悄失效了</b>：配置的超时时间不再起作用，
 * 而且没有任何警告。这时必须依靠 HTTP 客户端自身的超时（见 L1-03）。
 */
@SpringBootTest(classes = FactorySourceApplication.class,
        properties = "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true")
class DisableThreadPoolTest {

    @Autowired
    CircuitBreakerFactory<?, ?> factory;

    @AfterEach
    void cleanUp() {
        UserContext.clear();
    }

    @Test
    void 关闭线程池后业务代码运行在调用方线程中() {
        UserContext.set("kai");
        String thread = factory.create("plain").run(() -> Thread.currentThread().getName() + "/" + UserContext.get());
        assertThat(thread).isEqualTo(Thread.currentThread().getName() + "/kai");
    }

    @Test
    void 关闭线程池后TimeLimiter不再生效() {
        long start = System.nanoTime();
        // slow 的超时时间配置为 300ms（见 src/test/resources/application.yml），业务代码耗时 800ms
        String result = factory.create("slow").run(() -> {
            sleep(800);
            return "业务正常返回";
        }, t -> "超时降级");
        long costMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n结果：%s，耗时 %dms（超时配置为 300ms，但没有生效）%n", result, costMs);
        assertThat(result).isEqualTo("业务正常返回");
        assertThat(costMs).isGreaterThanOrEqualTo(800);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
