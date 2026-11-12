package com.kai.learn.cbdemo.l3.source;

/**
 * 模拟业务中常见的 ThreadLocal 上下文：当前登录用户、租户 ID、灰度标记……
 * 它们和 MDC 中的 traceId 一样，都是「绑定在线程上」的。
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(String user) {
        CURRENT_USER.set(user);
    }

    public static String get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
