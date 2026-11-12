package com.kai.learn.cbdemo.l2.feign;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 【是什么】声明式的下游客户端。开启 {@code spring.cloud.openfeign.circuitbreaker.enabled} 后，
 * <b>每个方法</b>都会自动被一个熔断器包裹，不用再手写 {@code factory.create(...).run(...)}。
 *
 * 【原理】调用链：
 * <pre>
 *   JDK 动态代理（FeignCircuitBreakerInvocationHandler）
 *     → circuitBreakerFactory.create(熔断器名称, group).run(
 *           () -> 真正的 Feign HTTP 调用,
 *           throwable -> fallbackFactory.create(throwable).对应的方法(参数))
 * </pre>
 * 熔断器名称由 {@code CircuitBreakerNameResolver} 生成，默认格式类似 {@code DownstreamFeignClientdataString}
 * （类名 + 方法名 + 参数类型，去掉了非字母数字的字符）。运行后访问 {@code /demo/breakers} 可以看到实际的名称。
 *
 * 【坑】按方法名生成熔断器名称，很难在 YAML 中针对单个方法写配置。所以本模块开启了 group 模式：
 * {@code spring.cloud.openfeign.circuitbreaker.group.enabled=true}，以 {@code @FeignClient} 的 name 作为 group，
 * 再在 YAML 中用 {@code configs.downstream} 统一配置这个客户端的所有方法。
 */
@FeignClient(name = "downstream", url = "${downstream.base-url}", fallbackFactory = DownstreamFallbackFactory.class)
public interface DownstreamFeignClient {

    @GetMapping("/api/data")
    ApiResult<Map<String, Object>> data(@RequestParam("caller") String caller);

    @GetMapping("/api/slow")
    ApiResult<Map<String, Object>> slow(@RequestParam("delayMs") long delayMs);
}
