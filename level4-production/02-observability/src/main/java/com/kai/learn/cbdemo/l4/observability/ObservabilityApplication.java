package com.kai.learn.cbdemo.l4.observability;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * L4-02：把熔断器接入 Prometheus + Grafana。应用启动后会持续产生流量（{@link TrafficGenerator}），
 * 切换下游的故障模式，就能在 Grafana 看板上看到熔断器状态的变化。
 *
 * <p>代码导读顺序：application.yml → {@link TrafficGenerator} → 根目录 docker-compose.yml 与 monitoring/
 */
@SpringBootApplication
@EnableScheduling
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        return new DownstreamApi(builder.baseUrl(baseUrl).build());
    }
}
