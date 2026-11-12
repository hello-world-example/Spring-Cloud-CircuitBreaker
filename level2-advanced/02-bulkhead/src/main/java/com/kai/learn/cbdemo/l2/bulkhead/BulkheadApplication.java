package com.kai.learn.cbdemo.l2.bulkhead;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * L2-02：舱壁（Bulkhead）—— 限制「同时」调用某个下游的请求数。
 *
 * <p>代码导读顺序：application.yml → {@link BulkheadDemoController}
 */
@SpringBootApplication
public class BulkheadApplication {

    public static void main(String[] args) {
        SpringApplication.run(BulkheadApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        return new DownstreamApi(builder.baseUrl(baseUrl).build());
    }
}
