package com.kai.learn.cbdemo.l1.timeout;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 【是什么】按异常类型选择降级策略。
 *
 * 【为什么】「一律返回默认值」的降级是危险的：
 * <ul>
 *   <li>熔断中（CallNotPermittedException）：下游已知不可用，应该<b>快速失败</b>并返回兜底数据</li>
 *   <li>超时（TimeoutException）：下游<b>可能已经处理成功</b>了！对于写操作（比如下单），
 *       降级成「失败」可能造成重复下单，需要业务层做幂等或者查询确认</li>
 *   <li>下游 5xx / 网络错误：下游故障，可以降级</li>
 *   <li>下游 4xx：这是<b>调用方的错误</b>（参数错误、无权限），不应该降级，也不应该计入熔断失败率，
 *       而是原样抛给上层（见 application.yml 中的 ignore-exceptions）</li>
 * </ul>
 *
 * 【坑】降级函数收到的异常可能被线程池包装成 ExecutionException，所以要先 {@link Errors#unwrap}。
 * 本模块的实测结果见 README：Resilience4j 的 TimeLimiter 已经帮我们解包了一层，但不要依赖这种实现细节。
 */
@Component
public class FallbackResolver {

    public ApiResult<Map<String, Object>> resolve(Throwable throwable) {
        Throwable root = Errors.unwrap(throwable);
        return switch (root) {
            case CallNotPermittedException e -> ApiResult.fallback("熔断中，快速失败", Map.of("item", "缓存中的商品"));
            case TimeoutException e -> ApiResult.fallback("调用超时，结果未知（写操作要做幂等或查询确认）");
            case HttpServerErrorException e -> ApiResult.fallback("下游故障：HTTP " + e.getStatusCode().value());
            case ResourceAccessException e -> ApiResult.fallback("网络错误：" + e.getMessage());
            // 4xx 原样抛出，交给全局异常处理（TimeoutDemoController#onClientError）。
            // 【原理】在降级函数中抛出的异常会直接从 run() 传播给调用方
            case HttpClientErrorException e -> throw e;
            default -> ApiResult.fallback("未知错误：" + Errors.describe(root));
        };
    }
}
