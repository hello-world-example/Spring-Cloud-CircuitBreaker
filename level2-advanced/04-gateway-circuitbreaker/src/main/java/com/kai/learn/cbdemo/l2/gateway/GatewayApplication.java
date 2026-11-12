package com.kai.learn.cbdemo.l2.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L2-04：在网关层做熔断。路由和熔断全部通过 application.yml 声明，Java 代码只有降级接口。
 *
 * <p>代码导读顺序：application.yml → {@link FallbackController}
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
