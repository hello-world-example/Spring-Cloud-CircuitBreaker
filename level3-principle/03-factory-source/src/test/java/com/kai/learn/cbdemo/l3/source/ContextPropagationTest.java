package com.kai.learn.cbdemo.l3.source;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 替换 Factory 的线程池之后，线程仍然会切换，但上下文被正确地搬运过去了。
 */
@SpringBootTest(classes = FactorySourceApplication.class, properties = "demo.propagate-context=true")
class ContextPropagationTest {

    @Autowired
    CircuitBreakerFactory<?, ?> factory;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        UserContext.clear();
    }

    @Test
    void create_id_的调用能拿到上下文() {
        MDC.put("traceId", "trace-123");
        UserContext.set("kai");

        Map<String, String> seen = factory.create("plain").run(ContextPropagationTest::capture);

        assertThat(seen.get("thread")).isNotEqualTo(Thread.currentThread().getName());
        assertThat(seen).containsEntry("traceId", "trace-123").containsEntry("user", "kai");
    }

    @Test
    void create_id_group_的调用使用另一个线程池_同样需要替换() {
        MDC.put("traceId", "trace-456");
        UserContext.set("kai");

        // 【动手】把 ContextPropagationConfig 中的 configureGroupExecutorService 注释掉，这个测试就会失败
        Map<String, String> seen = factory.create("order-api", "order-service").run(ContextPropagationTest::capture);

        assertThat(seen).containsEntry("traceId", "trace-456").containsEntry("user", "kai");
    }

    private static Map<String, String> capture() {
        return Map.of(
                "thread", Thread.currentThread().getName(),
                "traceId", String.valueOf(MDC.get("traceId")),
                "user", String.valueOf(UserContext.get()));
    }
}
