# L2-06 切换熔断实现

## 学习目标

- Spring Cloud CircuitBreaker 的抽象层统一了什么、没有统一什么？
- Resilience4j、Spring Retry、Framework Retry 三种实现的行为差异有多大？
- 选型时应该怎样验证？

## 模块结构

```
06-switch-implementation/
├── business/              业务代码，只依赖 spring-cloud-commons（抽象接口）
├── resilience4j-app/      + spring-cloud-starter-circuitbreaker-resilience4j
├── spring-retry-app/      + spring-cloud-starter-circuitbreaker-spring-retry
└── framework-retry-app/   + spring-cloud-starter-circuitbreaker-framework-retry（2025.1 新增）
```

三个 app 使用同一个端口 8206，一次只运行一个。

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/06-switch-implementation/resilience4j-app spring-boot:run
# 停止后依次换成 spring-retry-app、framework-retry-app
```

每个 app 都执行：

```bash
curl -X POST 'localhost:8081/chaos?mode=ERROR'
curl 'localhost:8206/demo/burst?n=8&intervalMs=200'
```

## 实测对比（下游持续返回 500，连续调用 8 次）

`calls` 是这次 run() 实际调用下游的次数：0 = 被熔断拦截，>1 = 发生了重试。

| 次数 | Resilience4j | Spring Retry | Framework Retry |
|---|---|---|---|
| 1 | calls=1 降级 | calls=1 降级 | **calls=3**（重试 2 次）降级，260ms |
| 2 | calls=1 降级 | calls=1 降级 | calls=0 熔断 |
| 3~5 | calls=1 降级 | calls=1 降级 | calls=0 熔断 |
| 6~8 | **calls=0 熔断** | calls=1 降级 | calls=0 熔断 |
| 结论 | 窗口内失败率 ≥ 50% 才打开 | **始终没有打开**（连续 20 次也一样） | 一次 run() 的重试全部失败就打开 |

## 代码导读

1. [InventoryService](business/src/main/java/com/kai/learn/cbdemo/l2/switchimpl/business/InventoryService.java)：只依赖抽象的业务代码
2. [Resilience4jApp](resilience4j-app/src/main/java/com/kai/learn/cbdemo/l2/switchimpl/resilience4j/Resilience4jApp.java)
3. [SpringRetryApp](spring-retry-app/src/main/java/com/kai/learn/cbdemo/l2/switchimpl/springretry/SpringRetryApp.java)：**为什么不会打开**的源码分析
4. [FrameworkRetryApp](framework-retry-app/src/main/java/com/kai/learn/cbdemo/l2/switchimpl/frameworkretry/FrameworkRetryApp.java)：Framework Retry 的状态机

## 原理要点

- 抽象层只统一了**调用方式**：`run(supplier, fallback)`。对于「什么算失败、什么时候打开、要不要重试、怎么恢复」，三种实现的模型完全不同。
- Resilience4j：滑动窗口 + 失败率，不重试。
- Framework Retry：在一次 run() 中无状态重试，重试耗尽一次就打开；openTimeout 后进入 HALF_OPEN；距离上次失败超过 resetTimeout 时重置。
- Spring Retry：有状态重试，计数保存在 RetryContext 中。但每次降级都会把 RetryContext 从缓存中删除，计数永远到不了阈值（见 `SpringRetryApp` 中的源码分析）。

## 动手练习

1. 在 `SpringRetryApp` 中加上 `.stateClassifier(t -> true)`，再运行 burst，接口返回什么？为什么降级没有生效？
2. 把 Framework Retry 的 `maxRetries` 改为 0，行为和 Resilience4j 有什么不同？
3. 让下游进入 `FLAKY 30%` 模式，分别运行三个 app 的 `burst?n=30`，比较降级次数。

## 思考题

<details>
<summary>1. 既然抽象层不能保证行为一致，它的价值在哪里？</summary>

价值在于**集成点统一**：OpenFeign、Gateway、HTTP Interface、LoadBalancer 等组件只依赖抽象接口，不用分别适配每一种熔断实现。
但对业务来说，换实现等于换一套容错语义，必须重新验证，不能当成「改个依赖就行」。
</details>

<details>
<summary>2. 什么场景下会选择 Framework Retry？</summary>

不想引入第三方依赖、调用量不大、主要诉求是「重试几次，还不行就冷却一段时间」的场景，比如调用低频的内部管理接口。
高并发场景下，基于失败率的 Resilience4j 更稳妥：偶发的单次失败不会导致熔断。
</details>
