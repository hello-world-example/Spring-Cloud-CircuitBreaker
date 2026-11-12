# L2-02 舱壁隔离（Bulkhead）

## 学习目标

- 舱壁解决什么问题？和熔断有什么区别？
- 线程池舱壁和信号量舱壁的行为有什么不同？
- Spring Cloud 如何为某个 id 选择舱壁类型？
- 经过 Spring Cloud 包装之后，业务代码到底运行在哪个线程上？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/02-bulkhead spring-boot:run
```

执行 [requests.http](requests.http)。实测结果（8 个并发，下游耗时 1s）：

```
tp-bulkhead（线程池：2 线程 + 2 队列）      success 4  rejected 4
  4 个 BulkheadFullException               15ms   ← 立即拒绝
  2 个 downstream  1149ms  pool-2-thread-1/2     ← 直接执行
  2 个 downstream  2164ms  pool-2-thread-1/2     ← 排队，等前两个完成后才执行

sem-bulkhead（信号量：2 个并发）           success 2  rejected 6
  6 个 BulkheadFullException               1ms
  2 个 downstream  1021ms

default-bulkhead（没有配置，默认线程池舱壁）  success 8  rejected 0
```

注意 `supplierThread` 一直是 `pool-2-thread-N`：这是 Factory 内部的线程池，而不是舱壁的线程，见下面的原理要点。

## 代码导读

1. [pom.xml](pom.xml)：舱壁需要额外引入 `resilience4j-bulkhead`
2. [application.yml](src/main/resources/application.yml)：`thread-pool-bulkhead` 与 `bulkhead` 两种配置
3. [BulkheadDemoController](src/main/java/com/kai/learn/cbdemo/l2/bulkhead/BulkheadDemoController.java)：**核心**，包括舱壁类型的选择规则和线程模型

## 原理要点

- 熔断针对「下游坏了」，舱壁针对「下游慢了」：限制同时调用某个下游的请求数，防止一个慢依赖耗尽全部线程。
- 选择规则（`Resilience4jBulkheadProvider#useSemaphoreBulkhead`）：`thread-pool-bulkhead` 中有配置 → 线程池；
  `bulkhead` 中有配置或 `enableSemaphoreDefaultBulkhead=true` → 信号量；否则默认线程池。
- 嵌套关系（`Resilience4JCircuitBreaker#run`）：`CircuitBreaker( Bulkhead( TimeLimiter( executor.submit(业务代码) ) ) )`。
  使用线程池舱壁时，**一次调用占用两个线程**：舱壁线程等待 Factory 线程池的结果。
- 舱壁的 key 是 group 名：`create(id, group)` 时同组的 id 共用一个舱壁。
- 舱壁拒绝（`BulkheadFullException`）默认也会计入熔断器的失败率。

## 动手练习

1. 把 `sem-bulkhead` 的 `max-wait-duration` 改为 `1500ms`，8 个并发的结果会变成什么样？
2. 设置 `spring.cloud.circuitbreaker.resilience4j.disable-thread-pool: true`，再看 `supplierThread`，线程变成了谁？
3. 把熔断器的 `failure-rate-threshold` 改回 50、`minimum-number-of-calls` 改为 5，多运行几次 tp-bulkhead，熔断器会被舱壁拒绝打开吗？这是你想要的吗？

## 思考题

<details>
<summary>1. 舱壁拒绝应不应该计入熔断的失败率？</summary>

一般不应该。舱壁拒绝说明「调用方的并发太高」，不说明下游不健康；计入失败率可能在流量高峰时误开熔断。
可以在熔断器配置中把 `io.github.resilience4j.bulkhead.BulkheadFullException` 加入 `ignore-exceptions`。
</details>

<details>
<summary>2. 有了虚拟线程，还需要线程池舱壁吗？</summary>

虚拟线程让「线程耗尽」不再是主要问题，但「对下游的并发数」仍然需要限制，否则会把下游压垮或者耗尽连接池。
这时用信号量舱壁更合适：开销小，限制的正是并发数。
</details>
