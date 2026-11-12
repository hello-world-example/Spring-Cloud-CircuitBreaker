package com.kai.learn.cbdemo.l2.feign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * L2-03：OpenFeign + CircuitBreaker。
 *
 * <p>代码导读顺序：application.yml → {@link DownstreamFeignClient} → {@link DownstreamFallbackFactory}
 * → {@link FeignDemoController}
 */
@SpringBootApplication
@EnableFeignClients
public class FeignApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeignApplication.class, args);
    }
}
