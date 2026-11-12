package com.kai.learn.cbdemo.l3.mini;

/**
 * 熔断器拒绝调用时抛出，对应 Resilience4j 的 {@code CallNotPermittedException}。
 *
 * 【原理】它不需要堆栈信息：OPEN 状态下每个请求都会抛出这个异常，填充堆栈（fillInStackTrace）的开销不小。
 * 本实现直接把 writableStackTrace 设为 false。
 *
 * 【对比】Resilience4j 把它做成了配置项：{@code writable-stack-trace-enabled}，<b>默认为 true</b>（保留堆栈）。
 * 高并发服务可以考虑设为 false，代价是排查问题时看不到是哪段代码触发的调用。
 */
public class MiniCallNotPermittedException extends RuntimeException {

    public MiniCallNotPermittedException(String name) {
        super("CircuitBreaker '" + name + "' is OPEN and does not permit further calls", null, false, false);
    }
}
