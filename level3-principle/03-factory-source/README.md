# L3-03 Factory 源码与线程模型

## 学习目标

- `factory.create(id).run(...)` 背后发生了什么？TimeLimiter、Bulkhead、CircuitBreaker 是怎样组合的？
- 为什么业务代码默认运行在另一个线程中？会带来什么问题？
- 怎样修复上下文（traceId、登录用户）丢失的问题？`disable-thread-pool` 能不能用？

## 运行

```bash
./mvnw -pl level3-principle/03-factory-source test
```

实测输出：

```
ThreadSwitchTest         调用方线程：main
                         run() 中看到的：{thread=pool-4-thread-1, traceId=null, user=null}   ← 上下文丢失
ContextPropagationTest   替换线程池后：traceId=trace-123, user=kai                          ← 修复
TimeLimiterDefaultTest   结果：超时降级：TimeoutException，耗时 308ms
DisableThreadPoolTest    结果：业务正常返回，耗时 813ms（超时配置为 300ms，但没有生效）       ← 代价
```

## 源码导读

建议在 IDE 中打开以下类，按顺序阅读（spring-cloud-circuitbreaker-resilience4j 5.0.3）：

| 步骤 | 类 / 方法 | 看什么 |
|---|---|---|
| 1 | `Resilience4JAutoConfiguration` | Factory 怎样创建；`Customizer` 在哪里被应用；舱壁什么时候启用 |
| 2 | `Resilience4JCircuitBreakerFactory` 的字段 | `executorService = Executors.newCachedThreadPool()`：**默认就有线程池** |
| 3 | `Resilience4JCircuitBreakerFactory#create(String)` / `create(String, String)` | 两个线程池；配置的查找顺序（id → group → 默认） |
| 4 | `Resilience4JCircuitBreaker#run` | 四个分支：有无舱壁 × 有无 executorService |
| 5 | `Resilience4jBulkheadProvider#decorateCallable` | 线程池舱壁 / 信号量舱壁的选择 |
| 6 | `ConfigurationPropertiesUtils#isDisableTimeLimiter` | disable-time-limiter 的 instance > group > global 优先级 |

`Resilience4JCircuitBreaker#run` 四个分支的组合关系：

```
                      有 executorService（默认）                     无 executorService（disable-thread-pool=true）
有舱壁    CB( Bulkhead( TimeLimiter( executor.submit(toRun) ) ) )     CB( Bulkhead( toRun ) )
无舱壁    CB( TimeLimiter( executor.submit(toRun) ) )                CB( toRun )                    ← 没有 TimeLimiter
```

建议的断点：`Resilience4JCircuitBreaker#run` 第一行，以及 `TimeLimiterImpl#decorateFutureSupplier` 中的 `future.get(timeout)`。

## 代码导读

1. [ThreadSwitchTest](src/test/java/com/kai/learn/cbdemo/l3/source/ThreadSwitchTest.java)：问题重现，以及对应的源码片段
2. [ContextPropagatingExecutorService](src/main/java/com/kai/learn/cbdemo/l3/source/ContextPropagatingExecutorService.java)：**核心**，快照 → 恢复 → 清理
3. [ContextPropagationConfig](src/main/java/com/kai/learn/cbdemo/l3/source/ContextPropagationConfig.java)：两个线程池都要替换
4. [DisableThreadPoolTest](src/test/java/com/kai/learn/cbdemo/l3/source/DisableThreadPoolTest.java) / [TimeLimiterDefaultTest](src/test/java/com/kai/learn/cbdemo/l3/source/TimeLimiterDefaultTest.java)：关闭线程池的代价

## 原理要点

- 切换线程是为了让 TimeLimiter 能够通过 `Future.get(timeout)` 实现超时。
- 代价：ThreadLocal（MDC、用户上下文、事务）全部丢失；线上表现为熔断保护的那一段日志没有 traceId。
- 修复：用会搬运上下文的线程池替换 `configureExecutorService` 和 `configureGroupExecutorService`；
  生产环境推荐 Micrometer context-propagation 或 Spring 的 `ContextPropagatingTaskDecorator`。
- `disable-thread-pool=true` 不切换线程，但 **TimeLimiter 会悄悄失效**，必须依靠 HTTP 客户端的超时。
- 使用线程池舱壁时还会再切换一次线程（舱壁线程），舱壁的上下文传递要用 `ThreadPoolBulkheadConfig.contextPropagator(...)`。
- 详见 [docs/04-thread-and-context.md](../../docs/04-thread-and-context.md)。

## 动手练习

1. 注释掉 `ContextPropagationConfig` 中的 `configureGroupExecutorService`，运行 `ContextPropagationTest`，哪个测试失败了？
2. 去掉 `ContextPropagatingExecutorService` 中 finally 里的清理代码，写一个测试证明上下文会「串」到下一个任务。
3. 引入 `resilience4j-bulkhead`，再运行 `ThreadSwitchTest`，线程名变成了什么？上下文还能被正确搬运吗？

## 思考题

<details>
<summary>1. 用了虚拟线程（spring.threads.virtual.enabled=true），Factory 的线程池会变成虚拟线程吗？</summary>

不会。`Executors.newCachedThreadPool()` 是在 Factory 中硬编码的，与 Spring Boot 的虚拟线程开关无关。
如果想让它使用虚拟线程，需要自己调用 `configureExecutorService(Executors.newVirtualThreadPerTaskExecutor())`
（同样要用上下文传递包装一下）。
</details>
