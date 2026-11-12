package com.kai.learn.cbdemo.l2.feign;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * 【是什么】降级实现的工厂：每次降级时用失败原因（Throwable）创建一个降级实现。
 *
 * 【对比】{@code @FeignClient} 有两种降级方式：
 * <ul>
 *   <li>{@code fallback = Xxx.class}：降级实现拿不到失败原因，无法区分「熔断中」和「超时」</li>
 *   <li>{@code fallbackFactory = Xxx.class}（本例）：可以拿到 Throwable，按原因选择不同的降级策略（见 L1-03）</li>
 * </ul>
 *
 * 【坑】降级类必须是 Spring Bean（加上 @Component），否则启动时报 "No fallbackFactory instance of type ..."。
 */
@Component
public class DownstreamFallbackFactory implements FallbackFactory<DownstreamFeignClient> {

    @Override
    public DownstreamFeignClient create(Throwable cause) {
        String reason = Errors.describe(cause);
        return new DownstreamFeignClient() {
            @Override
            public ApiResult<Map<String, Object>> data(String caller) {
                return ApiResult.fallback(reason, Map.of("item", "默认商品（Feign 降级）"));
            }

            @Override
            public ApiResult<Map<String, Object>> slow(long delayMs) {
                return ApiResult.fallback(reason);
            }
        };
    }
}
