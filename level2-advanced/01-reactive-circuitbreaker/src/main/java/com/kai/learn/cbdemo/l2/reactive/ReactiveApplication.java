package com.kai.learn.cbdemo.l2.reactive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L2-01：响应式熔断（WebClient + ReactiveCircuitBreakerFactory）。
 *
 * <p>代码导读顺序：{@link ReactiveProductService} → {@link ReactiveDemoController} → application.yml
 */
@SpringBootApplication
public class ReactiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactiveApplication.class, args);
    }
}
