package com.kai.learn.cbdemo.l4.layered;

import java.net.http.HttpClient;
import java.time.Duration;

import com.kai.learn.cbdemo.common.DownstreamApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * L4-03：强弱依赖与分层保护 —— 一个慢的弱依赖，怎样拖垮整个页面，以及怎样避免。
 *
 * <p>代码导读顺序：{@link ProductPageService} → application.yml → {@link LayeredDemoController}
 */
@SpringBootApplication
public class LayeredApplication {

    public static void main(String[] args) {
        SpringApplication.run(LayeredApplication.class, args);
    }

    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build());
        // 读超时设得比较大，用来模拟「没有配置合理超时」的常见情况；保护措施全部由熔断层提供
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new DownstreamApi(builder.baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
