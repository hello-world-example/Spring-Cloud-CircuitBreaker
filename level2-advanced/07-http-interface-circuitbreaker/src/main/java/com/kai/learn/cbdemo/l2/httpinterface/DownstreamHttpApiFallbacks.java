package com.kai.learn.cbdemo.l2.httpinterface;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.Errors;

/**
 * 【是什么】{@link DownstreamHttpApi} 的降级方法集合。
 *
 * 【原理】匹配规则（源码 {@code CircuitBreakerConfigurerUtils#getFallback}）：
 * <ol>
 *   <li>先找<b>同名、同参数</b>的方法，例如 {@code data(String)}</li>
 *   <li>找不到时，再找<b>第一个参数是 Throwable</b>、其余参数相同的方法，例如 {@code data(Throwable, String)}</li>
 *   <li>都找不到 → 抛出 NoFallbackAvailableException</li>
 * </ol>
 * 本类只提供了带 Throwable 的版本，所以能拿到失败原因。
 *
 * 【坑】类和方法都必须是 public；类需要有无参构造器，因为框架直接实例化它，而不是从容器中获取。
 */
public class DownstreamHttpApiFallbacks {

    public ApiResult<Map<String, Object>> data(Throwable cause, String caller) {
        return ApiResult.fallback(Errors.describe(cause), Map.of("item", "默认商品（HTTP Interface 降级）"));
    }

    public ApiResult<Map<String, Object>> slow(Throwable cause, long delayMs) {
        return ApiResult.fallback(Errors.describe(cause));
    }
}
