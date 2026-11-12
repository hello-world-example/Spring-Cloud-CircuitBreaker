package com.kai.learn.cbdemo.l4.ops;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * L4-04：运维开关 —— 在不重启应用的情况下，手动干预熔断器（强制熔断、关闭熔断、只统计不拦截），以及动态调整参数。
 *
 * <p>代码导读顺序：{@link OpsController} → {@link DemoController}
 */
@SpringBootApplication
public class OpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        return new DownstreamApi(builder.baseUrl(baseUrl).build());
    }
}
