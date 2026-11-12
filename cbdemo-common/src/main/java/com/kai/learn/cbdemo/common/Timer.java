package com.kai.learn.cbdemo.common;

import java.util.function.Supplier;

/**
 * 计时小工具：执行一段逻辑，同时返回结果和耗时。属于纯「噪音」代码，所以下沉到 common。
 */
public final class Timer {

    private Timer() {
    }

    public record Timed<T>(T value, long costMs) {
    }

    public static <T> Timed<T> time(Supplier<T> supplier) {
        long start = System.nanoTime();
        T value = supplier.get();
        return new Timed<>(value, (System.nanoTime() - start) / 1_000_000);
    }
}
