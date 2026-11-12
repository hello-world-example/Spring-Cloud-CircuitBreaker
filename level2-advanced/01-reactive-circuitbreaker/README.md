# L2-01 响应式熔断

## 学习目标

- WebFlux 应用中为什么要用 `ReactiveCircuitBreakerFactory`，而不是阻塞版本？
- 响应式熔断是怎样实现超时的？有没有线程切换？
- `run(Mono, fallback)` 的使用要点是什么？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/01-reactive-circuitbreaker spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

```
/demo/call            → data.handledBy = reactor-http-nio-4（Netty 事件循环线程，没有额外的线程池）

/demo/burst（下游 ERROR）
1  fallback  InternalServerError ...      CLOSED
...
4  fallback  InternalServerError ...      OPEN
5  fallback  CallNotPermittedException    OPEN   1ms

/demo/timeout?delayMs=1000
→ TimeoutException: Did not observe any item or terminal signal within 500ms in 'circuitBreaker'，costMs=516
```

超时的异常信息来自 Reactor 的 `timeout` 操作符，而不是 Resilience4j 的 TimeLimiter，可以证明响应式版本的超时是用操作符实现的。

## 代码导读

1. [ReactiveProductService](src/main/java/com/kai/learn/cbdemo/l2/reactive/ReactiveProductService.java)：**核心**，包括源码中操作符的组装顺序
2. [ReactiveDemoController](src/main/java/com/kai/learn/cbdemo/l2/reactive/ReactiveDemoController.java)：用 `concatMap` 实现顺序调用
3. [application.yml](src/main/resources/application.yml)：与阻塞版本完全相同的配置项

## 原理要点

- 源码 `ReactiveResilience4JCircuitBreaker#run`：`toRun.transform(CircuitBreakerOperator).timeout(...).doOnError(TimeoutException → cb.onError).onErrorResume(fallback)`。
- `timeout` 位于熔断操作符的外层，超时时熔断操作符只收到「取消」信号，所以源码要手动补一次 `cb.onError`，把超时计为失败。
- 响应式版本只支持**信号量舱壁**（线程池舱壁与非阻塞模型冲突）。

## 动手练习

1. 在 `getProduct()` 中把 `call` 改成先 `.block()` 再用 `Mono.just(...)` 包装后传给 `run()`，下游报错时还能熔断吗？为什么？
2. 用 `Flux.range(1, 8).flatMap(...)`（并发）替换 `concatMap`（顺序），观察 burst 的结果有什么变化。

## 思考题

<details>
<summary>1. 响应式应用中，ThreadLocal 丢失的问题还存在吗？</summary>

问题换了一种形式：响应式链路本来就可能在不同线程间跳转，ThreadLocal 从一开始就不可靠，需要使用 Reactor Context，并配合 Micrometer 的 context-propagation 库在 Reactor Context 与 ThreadLocal（MDC、traceId）之间自动桥接。
</details>
