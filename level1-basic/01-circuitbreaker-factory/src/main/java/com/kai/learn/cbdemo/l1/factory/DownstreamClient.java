package com.kai.learn.cbdemo.l1.factory;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 【是什么】对下游服务的普通 HTTP 调用，<b>不包含任何熔断逻辑</b>。
 *
 * 【为什么】把「调用」和「容错」分开写：这个类只负责发请求，熔断交给 {@link ProductService}。
 * 这也是熔断器的设计思路，它是一个<b>装饰器</b>，包在任意一段代码（{@code Supplier}）的外面，不关心里面在做什么。
 *
 * 【坑】下游返回 5xx 时，RestClient 默认抛出 {@code HttpServerErrorException}，返回 4xx 时抛出 {@code HttpClientErrorException}。
 * 熔断器只能感知到<b>异常</b>：如果你自己 catch 住异常再返回一个错误对象，熔断器会认为调用成功。
 */
@Component
public class DownstreamClient {

    private static final ParameterizedTypeReference<ApiResult<Map<String, Object>>> TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public DownstreamClient(RestClient.Builder builder, @Value("${downstream.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ApiResult<Map<String, Object>> getData(String caller) {
        return restClient.get()
                .uri("/api/data?caller={caller}", caller)
                .retrieve()
                .body(TYPE);
    }
}
