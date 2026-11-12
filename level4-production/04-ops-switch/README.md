# L4-04 运维开关与动态调参

## 学习目标

- 线上什么时候需要人工干预熔断器？
- FORCED_OPEN、DISABLED、METRICS_ONLY 三个特殊状态分别做什么？
- 怎样在不重启的情况下调整熔断参数？有什么副作用？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level4-production/04-ops-switch spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

```
1 正常            {"state":"CLOSED",       "downstream":5,  "fallback":{}}
2 强制熔断        {"state":"FORCED_OPEN",  "downstream":0,  "fallback":{"CallNotPermittedException":5}}   ← 下游健康也全部拒绝
3 关闭熔断+下游故障 {"state":"DISABLED",    "downstream":0,  "fallback":{"InternalServerError":10}}       ← 全部失败，但不会熔断
4 只统计不拦截    {"state":"METRICS_ONLY", ...}  /ops/breakers → failureRate=100.0，state 仍为 METRICS_ONLY
6 动态调参 90%    {"state":"CLOSED", "failureRateThreshold":90.0, "bufferedCalls":0}                       ← 统计数据被清空
  FLAKY 60% 下    {"state":"CLOSED", "downstream":8, "fallback":{"InternalServerError":12}}            ← 60% < 90%，不熔断
```

## 代码导读

1. [OpsController](src/main/java/com/kai/learn/cbdemo/l4/ops/OpsController.java)：**核心**，三个特殊状态、动态调参，以及各自的坑
2. [DemoController](src/main/java/com/kai/learn/cbdemo/l4/ops/DemoController.java)：产生调用

## 原理要点

| 状态 | 是否放行 | 是否统计 | 是否自动切换 | 典型用途 |
|---|---|---|---|---|
| FORCED_OPEN | 否 | 否 | 否 | 下游发布 / 已知故障，主动切断 |
| DISABLED | 是 | 否 | 否 | 熔断误判，先保证业务可用 |
| METRICS_ONLY | 是 | 是 | 否 | 新接入时先观察，再定阈值 |

- 三个特殊状态都不会自动恢复，必须人工 `close` 或 `reset`。
- 动态调参是**替换整个熔断器实例**（`registry.replace`）：统计数据清零、旧实例上的事件监听器丢失、只对当前这个实例（Pod）生效。
- Spring Cloud 每次 `run()` 都会从 Registry 中重新获取实例，所以替换后立即生效。

## 动手练习

1. 在 L2-05 的 `CircuitBreakerEventRecorder` 中处理 `onEntryReplacedEvent`，让动态调参后事件监听仍然有效。
2. 给 `/ops/**` 加上一个简单的 Token 鉴权（`HandlerInterceptor`），没有 Token 时返回 401。
3. 设计一个方案：通过配置中心（Nacos / Spring Cloud Config）下发熔断参数，所有实例同时生效。

## 思考题

<details>
<summary>1. 强制熔断和「降级开关」是一回事吗？</summary>

效果类似（都不再调用下游），但出发点不同。强制熔断是针对「下游」的保护；降级开关通常是针对「功能」的业务决策，
比如大促时关闭推荐、评论等非核心功能，把资源留给下单链路。降级开关一般放在配置中心，由业务代码判断，
而不是借用熔断器的状态。
</details>
