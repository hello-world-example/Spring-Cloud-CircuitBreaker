package com.kai.learn.cbdemo.l1.config;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * L1-02：熔断器的配置方式与优先级。
 *
 * <p>代码导读顺序：application.yml → {@link CircuitBreakerCustomizerConfig} → {@link ConfigDemoController}
 */
@SpringBootApplication
public class ConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        return new DownstreamApi(builder.baseUrl(baseUrl).build());
    }
}
