package com.kai.learn.cbdemo.common;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 异常描述小工具：把降级收到的异常转成一行简短的说明，便于在示例输出中阅读。
 */
public final class Errors {

    private static final int MAX = 120;

    private Errors() {
    }

    /**
     * 剥掉线程池、CompletableFuture 包装出来的外层异常，得到真正的原因。
     * 【坑】一旦代码在其他线程中执行，异常就可能被包装成 ExecutionException / CompletionException，
     * 降级时如果只按异常类型判断（instanceof），就会判断错误。
     */
    public static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while ((cur instanceof ExecutionException || cur instanceof CompletionException) && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    public static String describe(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        Throwable root = unwrap(t);
        String msg = root.getClass().getSimpleName() + ": " + root.getMessage();
        return msg.length() > MAX ? msg.substring(0, MAX) + "…" : msg;
    }
}
