package com.kai.learn.cbdemo.l3.mini;

/**
 * 【是什么】按次数统计的滑动窗口：只记住最近 N 次调用的结果。
 *
 * 【原理】环形缓冲区（ring buffer）+ 增量统计：
 * <pre>
 *   outcomes: [F][S][S][F][S]      size = 5
 *                     ^ head（下一次写入的位置）
 *   写入一次新结果：
 *     1. 如果窗口已满，先把 head 位置上「最老」的结果从统计中减掉
 *     2. 把新结果写入 head，加到统计中
 *     3. head 向后移动一格（到末尾后回到 0）
 * </pre>
 * 每次记录都是 O(1)，与窗口大小无关。Resilience4j 的 {@code FixedSizeSlidingWindowMetrics} 原理相同，
 * 只是它记录的是一个个「测量值」（Measurement），里面还包含耗时、慢调用次数等信息。
 *
 * 【对比】最朴素的写法是每次遍历整个窗口来计算失败率，复杂度是 O(N)。熔断器在每一次业务调用的路径上执行，
 * 所以必须足够轻量。
 */
class CountBasedSlidingWindow {

    record Snapshot(int totalCalls, int failedCalls) {
        /** 失败率（百分比）；样本不足时返回 -1，表示「不做判断」 */
        float failureRate(int minimumNumberOfCalls) {
            return totalCalls < minimumNumberOfCalls ? -1 : failedCalls * 100.0f / totalCalls;
        }
    }

    private final boolean[] failures;
    private int head;
    private int totalCalls;
    private int failedCalls;

    CountBasedSlidingWindow(int size) {
        this.failures = new boolean[size];
    }

    /**
     * 记录一次调用结果，并返回记录后的统计快照。
     * 【原理】加 synchronized 保证「减掉最老的 + 加上最新的」是一个原子操作。
     * Resilience4j 2.x 的 {@code FixedSizeSlidingWindowMetrics#record} 同样加锁，用的是 {@code ReentrantLock}：
     * 在 JDK 21 中，虚拟线程在 synchronized 块内阻塞会「钉住」（pin）载体线程，ReentrantLock 没有这个问题。
     * 临界区非常小，竞争开销可以接受。
     */
    synchronized Snapshot record(boolean failed) {
        if (totalCalls == failures.length) {
            // 窗口已满：移除最老的结果
            if (failures[head]) {
                failedCalls--;
            }
            totalCalls--;
        }
        failures[head] = failed;
        totalCalls++;
        if (failed) {
            failedCalls++;
        }
        head = (head + 1) % failures.length;
        return new Snapshot(totalCalls, failedCalls);
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(totalCalls, failedCalls);
    }
}
