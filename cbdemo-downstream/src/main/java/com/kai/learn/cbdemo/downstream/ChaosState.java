package com.kai.learn.cbdemo.downstream;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * 当前的故障模式。用 AtomicReference 保存一个不可变的 {@link Config}，切换模式时整体替换，线程安全。
 */
@Component
public class ChaosState {

    public enum Mode {
        /** 正常返回 */
        NORMAL,
        /** 延迟 delayMs 后再返回，用来模拟慢调用、超时 */
        SLOW,
        /** 一律返回 500 */
        ERROR,
        /** 按 failRate% 的概率返回 500 */
        FLAKY
    }

    public record Config(Mode mode, long delayMs, int failRate) {
    }

    private final AtomicReference<Config> config = new AtomicReference<>(new Config(Mode.NORMAL, 0, 0));
    private final AtomicLong receivedCount = new AtomicLong();

    public Config get() {
        return config.get();
    }

    public void set(Config newConfig) {
        config.set(newConfig);
    }

    public long incrementReceived() {
        return receivedCount.incrementAndGet();
    }

    public long receivedCount() {
        return receivedCount.get();
    }

    public void resetCount() {
        receivedCount.set(0);
    }

    /** FLAKY 模式下，本次请求是否应该失败 */
    public boolean shouldFail(int failRate) {
        return ThreadLocalRandom.current().nextInt(100) < failRate;
    }
}
