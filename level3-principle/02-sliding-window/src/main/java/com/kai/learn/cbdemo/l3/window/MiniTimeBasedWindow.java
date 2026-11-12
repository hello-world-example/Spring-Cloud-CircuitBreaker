package com.kai.learn.cbdemo.l3.window;

import java.time.Clock;
import java.util.Arrays;

/**
 * 【是什么】手写的按时间统计的滑动窗口：统计「最近 N 秒」内的调用。
 *
 * 【原理】分桶（bucket）：
 * <pre>
 *   windowSeconds = 5，每秒一个桶，桶数组循环使用（下标 = 秒 % 5）：
 *
 *   秒:      100   101   102   103   104   | 105 到来时
 *   下标:     0     1     2     3     4    |   0 ← 先清空（100 秒的数据已过期），再写入
 *   数据:   [3/1] [5/0] [2/2] [0/0] [4/1]  |
 *            总数/失败数
 * </pre>
 * 写入时只需要找到「当前秒」对应的桶，如果桶里存的是过期的数据就先重置；读取时把未过期的桶加起来。
 *
 * 【对比】Resilience4j 的 {@code SlidingTimeWindowMetrics} 做了两点优化：
 * <ul>
 *   <li>维护一个全局的 {@code TotalAggregation}：桶过期时把它的数据从总数中「减掉」，读取总数是 O(1)，
 *       而不是像本实现这样每次遍历所有桶</li>
 *   <li>桶内的数据（{@code PartialAggregation}）还包含耗时、慢调用数，用于慢调用统计</li>
 * </ul>
 *
 * 【坑】时间窗口的精度是「秒」：窗口为 10 秒时，实际覆盖的是 9~10 秒（当前这一秒只过去了一部分）。
 */
public class MiniTimeBasedWindow {

    private final int windowSeconds;
    private final long[] bucketEpochSecond;
    private final int[] bucketTotal;
    private final int[] bucketFailed;
    private final Clock clock;

    public MiniTimeBasedWindow(int windowSeconds, Clock clock) {
        this.windowSeconds = windowSeconds;
        this.bucketEpochSecond = new long[windowSeconds];
        this.bucketTotal = new int[windowSeconds];
        this.bucketFailed = new int[windowSeconds];
        this.clock = clock;
        Arrays.fill(bucketEpochSecond, -1);
    }

    public synchronized void record(boolean failed) {
        long now = clock.instant().getEpochSecond();
        int idx = (int) (now % windowSeconds);
        if (bucketEpochSecond[idx] != now) {
            // 这个桶里是 windowSeconds 秒之前的旧数据，重置
            bucketEpochSecond[idx] = now;
            bucketTotal[idx] = 0;
            bucketFailed[idx] = 0;
        }
        bucketTotal[idx]++;
        if (failed) {
            bucketFailed[idx]++;
        }
    }

    /** [总调用数, 失败数] */
    public synchronized int[] snapshot() {
        long now = clock.instant().getEpochSecond();
        int total = 0;
        int failed = 0;
        for (int i = 0; i < windowSeconds; i++) {
            // 只统计最近 windowSeconds 秒内的桶
            if (bucketEpochSecond[i] > now - windowSeconds) {
                total += bucketTotal[i];
                failed += bucketFailed[i];
            }
        }
        return new int[]{total, failed};
    }

    public float failureRate(int minimumNumberOfCalls) {
        int[] s = snapshot();
        return s[0] < minimumNumberOfCalls ? -1 : s[1] * 100.0f / s[0];
    }
}
