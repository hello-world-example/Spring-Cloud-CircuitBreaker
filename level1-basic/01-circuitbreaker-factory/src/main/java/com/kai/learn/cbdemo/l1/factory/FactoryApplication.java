package com.kai.learn.cbdemo.l1.factory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L1-01：用 {@code CircuitBreakerFactory} 包装一次远程调用，观察熔断器 CLOSED → OPEN → HALF_OPEN → CLOSED 的完整生命周期。
 *
 * <p>代码导读顺序：{@link DownstreamClient} → {@link ProductService} → {@link DemoController} → application.yml
 */
@SpringBootApplication
public class FactoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(FactoryApplication.class, args);
    }
}
