# L2-05 事件与指标

## 学习目标

- 怎样监听熔断器的状态切换，并据此打日志、发告警？
- 熔断器暴露了哪些 Micrometer 指标？在 Prometheus 中叫什么名字？
- Spring Boot 4 下，Resilience4j 自带的 Actuator 端点和健康检查还能用吗？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/05-events-metrics spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

**事件**（`/demo/events`，5 次成功 + 10 次失败之后）：

```
SUCCESS × 5, ERROR × 5, FAILURE_RATE_EXCEEDED × 1, STATE_TRANSITION × 1（CLOSED → OPEN）, NOT_PERMITTED × 5
```

**指标**（`/actuator/prometheus`）：

```
resilience4j_circuitbreaker_state{group="inventory-service",name="inventory",state="open"} 1.0
resilience4j_circuitbreaker_failure_rate{group="inventory-service",name="inventory"} 50.0
resilience4j_circuitbreaker_calls_seconds_count{kind="failed",...} 5
resilience4j_circuitbreaker_calls_seconds_count{kind="successful",...} 5
resilience4j_circuitbreaker_not_permitted_calls_total{...} 5.0
spring_cloud_circuitbreaker_active_seconds_count{...}         ← Spring Cloud 的 Observation 指标
```

**Spring Boot 4 兼容性**（实测，Resilience4j 2.3.0）：

| 功能 | 结果 |
|---|---|
| `resilience4j_*` Micrometer 指标 | ✅ 可用，并带有 group 标签 |
| `/actuator/circuitbreakers` 端点 | ✅ 可用 |
| `/actuator/health` 中的 circuitBreakers 健康检查 | ❌ 没有出现。Spring Boot 4 把健康检查的 API 迁移到了新的包中，而 resilience4j-spring-boot3 是基于 Boot 3 编译的 |

## 代码导读

1. [CircuitBreakerEventRecorder](src/main/java/com/kai/learn/cbdemo/l2/metrics/CircuitBreakerEventRecorder.java)：**核心**，Registry 事件与熔断器事件
2. [application.yml](src/main/resources/application.yml)：Actuator 暴露的端点
3. [MetricsDemoController](src/main/java/com/kai/learn/cbdemo/l2/metrics/MetricsDemoController.java)

## 原理要点

- 熔断器是懒加载创建的，所以要先监听 Registry 的 `EntryAddedEvent`，在熔断器被创建时再挂上事件监听器。
- 事件回调在业务线程中**同步**执行，回调中不能做慢操作。
- 指标由 resilience4j-micrometer 的 `TaggedCircuitBreakerMetrics` 绑定；`create(id, group)` 中的 group 会成为指标标签。
- 告警规则示例（PromQL）：`resilience4j_circuitbreaker_state{state="open"} == 1` 持续 1 分钟。完整的看板见 L4-02。

## 动手练习

1. 在 `CircuitBreakerEventRecorder` 中只在 `CLOSED → OPEN` 时打印告警，`HALF_OPEN → CLOSED` 时打印「恢复」。
2. 写一个自定义的 `HealthIndicator`（Spring Boot 4 的包是 `org.springframework.boot.health.contributor`），
   有熔断器处于 OPEN 状态时返回 `OUT_OF_SERVICE`。

## 思考题

<details>
<summary>1. 熔断器打开时，应该把实例的健康状态设为 DOWN 吗？</summary>

要非常谨慎。如果健康检查被 Kubernetes 用作存活探针（liveness），下游故障会导致上游所有 Pod 被重启，故障被放大。
熔断器状态更适合作为**告警**和**看板**的数据源，而不是健康检查。
</details>
