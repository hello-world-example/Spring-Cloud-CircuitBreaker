package com.kai.learn.cbdemo.common;

import java.time.Instant;

/**
 * 【是什么】所有示例统一使用的返回体。
 *
 * 【为什么】学习熔断时最关心的问题是「这次的结果是真实调用返回的，还是降级（fallback）返回的」。
 * 所以专门加了 {@code source} 字段：
 * <ul>
 *   <li>{@code downstream}：真实调用了下游服务</li>
 *   <li>{@code fallback}：走了降级逻辑，{@code message} 中写明降级原因</li>
 * </ul>
 *
 * @param success 业务是否成功
 * @param source  数据来源：downstream / fallback / gateway-fallback
 * @param message 说明信息；降级时是失败原因
 * @param data    业务数据
 * @param time    生成时间
 */
public record ApiResult<T>(boolean success, String source, String message, T data, Instant time) {

    public static final String SOURCE_DOWNSTREAM = "downstream";
    public static final String SOURCE_FALLBACK = "fallback";

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, SOURCE_DOWNSTREAM, "ok", data, Instant.now());
    }

    public static <T> ApiResult<T> fallback(String reason, T defaultData) {
        return new ApiResult<>(false, SOURCE_FALLBACK, reason, defaultData, Instant.now());
    }

    public static <T> ApiResult<T> fallback(String reason) {
        return fallback(reason, null);
    }
}
