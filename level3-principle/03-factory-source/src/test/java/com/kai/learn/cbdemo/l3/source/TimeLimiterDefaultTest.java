package com.kai.learn.cbdemo.l3.source;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对照组：默认配置（有线程池）下，同样的调用会在 300ms 时超时降级。
 */
@SpringBootTest(classes = FactorySourceApplication.class)
class TimeLimiterDefaultTest {

    @Autowired
    CircuitBreakerFactory<?, ?> factory;

    @Test
    void 默认配置下TimeLimiter生效() {
        long start = System.nanoTime();
        String result = factory.create("slow").run(() -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "业务正常返回";
        }, t -> "超时降级：" + t.getClass().getSimpleName());
        long costMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%n结果：%s，耗时 %dms%n", result, costMs);
        assertThat(result).isEqualTo("超时降级：TimeoutException");
        assertThat(costMs).isLessThan(700);
    }
}
