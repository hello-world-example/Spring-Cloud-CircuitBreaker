package com.kai.learn.cbdemo.l1.timeout;

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
 * L1-03：超时控制（TimeLimiter）与按异常类型降级。
 *
 * <p>代码导读顺序：application.yml → {@link FallbackResolver} → {@link TimeoutDemoController}
 */
@SpringBootApplication
public class TimeoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeoutApplication.class, args);
    }

    /**
     * 【原理】超时有两层，作用不同：
     * <ul>
     *   <li><b>HTTP 客户端超时</b>（这里的 connectTimeout / readTimeout）：真正中止网络 I/O，释放连接和线程</li>
     *   <li><b>TimeLimiter 超时</b>：只是让「调用方」不再等待，后台线程中的 HTTP 请求未必会停止（见 README 中的实验）</li>
     * </ul>
     * 【生产】两层都要配置，并且 HTTP 读超时应当 ≤ TimeLimiter 超时，否则 TimeLimiter 触发之后，
     * 后台线程还会被慢请求占用一段时间。这里故意把读超时（2s）设得比 fast-timeout 的 TimeLimiter（500ms）大，
     * 以便观察这个现象。
     */
    @Bean
    DownstreamApi downstreamApi(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return new DownstreamApi(builder.baseUrl(baseUrl).requestFactory(requestFactory).build());
    }
}
