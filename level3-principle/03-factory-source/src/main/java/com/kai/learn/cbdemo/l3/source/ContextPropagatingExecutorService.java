package com.kai.learn.cbdemo.l3.source;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;

/**
 * 【是什么】一个会「搬运上下文」的线程池包装器：提交任务时在<b>调用方线程</b>上拍下上下文的快照，
 * 执行任务时在<b>工作线程</b>上恢复，执行完再清理。
 *
 * 【原理】继承 {@link AbstractExecutorService} 后，submit()/invokeAll() 最终都会调用 {@link #execute(Runnable)}，
 * 所以只需要在 execute 中包装一层：
 * <pre>
 *   调用方线程                         工作线程
 *   execute(task)
 *     ├─ 快照 = MDC + UserContext
 *     └─ delegate.execute(包装后的任务) ──▶ 恢复快照 → task.run() → 清理（finally）
 * </pre>
 *
 * 【坑】
 * <ul>
 *   <li>必须在 finally 中清理：线程池中的线程会被复用，不清理的话上下文会「串」到下一个任务</li>
 *   <li>快照必须在 execute 时（调用方线程上）拍下，而不是在任务运行时</li>
 *   <li>Spring Cloud 的 Factory 有两个线程池：create(id) 使用 {@code configureExecutorService} 设置的线程池，
 *       create(id, group) 使用 {@code configureGroupExecutorService} 创建的线程池，<b>两个都要替换</b></li>
 * </ul>
 *
 * 【对比】生产环境中更推荐使用现成的方案：Micrometer 的 context-propagation 库
 * （{@code ContextSnapshotFactory}），或者 Spring 的 {@code ContextPropagatingTaskDecorator}，
 * 它们能自动搬运所有已注册的 ThreadLocal（包括 Micrometer Tracing 的 traceId）。
 */
public class ContextPropagatingExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;

    public ContextPropagatingExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        // 在调用方线程上拍快照
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        String user = UserContext.get();
        delegate.execute(() -> {
            // 在工作线程上恢复
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            UserContext.set(user);
            try {
                task.run();
            } finally {
                MDC.clear();
                UserContext.clear();
            }
        });
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
