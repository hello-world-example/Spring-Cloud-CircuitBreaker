package com.kai.learn.cbdemo.common;

/**
 * 【是什么】一次调用的观察记录，用于 {@code /demo/burst} 这类「连续调用 N 次」的接口。
 *
 * 【为什么】熔断器的行为要放在<b>一连串调用</b>中才看得出来：前几次失败 → 达到阈值 → OPEN → 直接拒绝 → 等待后 HALF_OPEN。
 * 把每次调用后的熔断器状态打印出来，状态切换就一目了然。
 *
 * @param seq     第几次调用（从 1 开始）
 * @param source  结果来源：downstream / fallback
 * @param message 结果说明（降级时是失败原因）
 * @param state   这次调用<b>结束后</b>熔断器的状态：CLOSED / OPEN / HALF_OPEN ……
 * @param costMs  耗时（毫秒）。OPEN 状态下拒绝请求几乎不耗时，这正是「快速失败」的意义
 * @param thread  执行业务调用的线程名，用来观察线程池舱壁、TimeLimiter 引起的线程切换
 */
public record CallRecord(int seq, String source, String message, String state, long costMs, String thread) {
}
