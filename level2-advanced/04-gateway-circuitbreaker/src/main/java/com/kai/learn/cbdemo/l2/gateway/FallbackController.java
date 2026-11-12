package com.kai.learn.cbdemo.l2.gateway;

import java.time.Instant;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 【是什么】网关熔断的降级端点：CircuitBreaker 过滤器中配置了 {@code fallbackUri: forward:/fallback/downstream}，
 * 熔断、超时或下游出错时，请求会在网关<b>内部转发</b>到这里。
 *
 * 【原理】失败原因被放在 exchange 的属性中：{@link ServerWebExchangeUtils#CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR}。
 * 降级端点可以根据异常类型返回不同的状态码，比如熔断时返回 503，超时时返回 504。
 *
 * 【坑】fallbackUri 只支持 {@code forward:} 协议（网关内部转发），不能直接写一个外部地址。
 * 如果想降级到另一个服务，需要再配置一条路由，让 forward 的路径匹配到那条路由。
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private final CircuitBreakerRegistry registry;

    public FallbackController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @RequestMapping("/downstream")
    public Mono<ResponseEntity<ApiResult<Map<String, Object>>>> downstream(ServerWebExchange exchange) {
        Throwable t = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String reason = Errors.describe(t);
        HttpStatus status = reason.startsWith("CallNotPermittedException") ? HttpStatus.SERVICE_UNAVAILABLE
                : reason.startsWith("TimeoutException") ? HttpStatus.GATEWAY_TIMEOUT
                : HttpStatus.BAD_GATEWAY;
        ApiResult<Map<String, Object>> body = new ApiResult<>(false, "gateway-fallback", reason,
                Map.of("breakerState", registry.circuitBreaker("downstreamCB").getState().name()), Instant.now());
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        var cb = registry.circuitBreaker("downstreamCB");
        return Map.of("state", cb.getState(), "failureRate", cb.getMetrics().getFailureRate(),
                "notPermittedCalls", cb.getMetrics().getNumberOfNotPermittedCalls());
    }
}
