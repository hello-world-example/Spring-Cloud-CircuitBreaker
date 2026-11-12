package com.kai.learn.cbdemo.l3.source;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * L3-03：拆解 {@code Resilience4JCircuitBreakerFactory} 与 {@code Resilience4JCircuitBreaker#run} 的源码，
 * 重点是<b>线程模型</b>以及由此引起的上下文（ThreadLocal / MDC）丢失问题。
 *
 * <p>本模块没有 main 方法要运行，所有实验都在 src/test 中：
 * <ol>
 *   <li>{@code ThreadSwitchTest}：证明默认情况下业务代码运行在另一个线程中，上下文丢失</li>
 *   <li>{@code ContextPropagationTest}：用 {@link ContextPropagatingExecutorService} 修复</li>
 *   <li>{@code DisableThreadPoolTest}：另一种「修复」—— 关闭线程池，以及它的代价</li>
 * </ol>
 * 源码导读见本模块 README.md。
 */
@SpringBootApplication
public class FactorySourceApplication {
}
