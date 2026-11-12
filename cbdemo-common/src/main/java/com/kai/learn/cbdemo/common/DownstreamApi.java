package com.kai.learn.cbdemo.common;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/**
 * 对 cbdemo-downstream 的同步 HTTP 调用封装，从 L1-02 开始统一使用这个类（L1-01 中有一份带讲解的同等实现）。
 *
 * <p>只负责发请求，<b>不包含任何容错逻辑</b>。下游返回 5xx 时抛出 HttpServerErrorException，返回 4xx 时抛出
 * HttpClientErrorException，熔断器靠这些异常来判断失败。
 */
public class DownstreamApi {

    private static final ParameterizedTypeReference<ApiResult<Map<String, Object>>> TYPE = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public DownstreamApi(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 行为由下游当前的故障模式决定 */
    public ApiResult<Map<String, Object>> data(String caller) {
        return restClient.get().uri("/api/data?caller={caller}", caller).retrieve().body(TYPE);
    }

    /** 固定延迟 delayMs 毫秒后返回 */
    public ApiResult<Map<String, Object>> slow(long delayMs) {
        return restClient.get().uri("/api/slow?delayMs={d}", delayMs).retrieve().body(TYPE);
    }

    /** 下游返回指定的 HTTP 状态码 */
    public ApiResult<Map<String, Object>> status(int code) {
        return restClient.get().uri("/api/status/{code}", code).retrieve().body(TYPE);
    }
}
