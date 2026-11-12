package com.kai.learn.cbdemo.l2.metrics;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * L2-05：熔断器的事件与指标 —— 生产环境中怎么「看见」熔断器。
 *
 * <p>代码导读顺序：{@link CircuitBreakerEventRecorder} → application.yml（actuator 部分）→ {@link MetricsDemoController}
 */
@SpringBootApplication
public class MetricsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetricsApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        return new DownstreamApi(builder.baseUrl(baseUrl).build());
    }
}
