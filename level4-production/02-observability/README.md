# L4-02 可观测性：Prometheus + Grafana

## 学习目标

- 怎样把熔断器的指标接入 Prometheus，并在 Grafana 中做成看板？
- 熔断相关的告警规则应该怎么写？
- 看板上应该关注哪几个指标？

## 运行

```bash
# 1. 启动 Prometheus + Grafana（需要 Docker）
docker compose up -d
# 2. 启动下游和本模块（本模块启动后会自动持续产生流量）
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level4-production/02-observability spring-boot:run
# 3. 制造故障，观察看板
curl -X POST 'localhost:8081/chaos?mode=ERROR'
curl -X POST 'localhost:8081/chaos?mode=FLAKY&failRate=40'
curl -X POST 'localhost:8081/chaos?mode=NORMAL'
```

- Grafana：http://localhost:3000 → Dashboards → cbdemo → 「cbdemo · 熔断器」（匿名访问，无需登录）
- Prometheus 告警：http://localhost:9090/alerts
- 原始指标：http://localhost:8402/actuator/prometheus

> 实测说明：应用端的指标已验证（`resilience4j_circuitbreaker_*` 指标、带 `group` 标签、调用耗时的直方图桶都能正常输出），
> Dashboard JSON 和 `docker compose config` 也校验通过。生成本项目时本机的 Docker 没有运行，
> **Prometheus 采集与 Grafana 看板的实际展示没有验证**。

## 看板内容

| 面板 | 指标 | 看什么 |
|---|---|---|
| 熔断器状态 | `resilience4j_circuitbreaker_state` | 每个熔断器在时间轴上的 CLOSED / OPEN / HALF_OPEN |
| 失败率 | `resilience4j_circuitbreaker_failure_rate` | 距离阈值（红线 50%）还有多远；-1 表示样本不足 |
| 每秒调用数 | `resilience4j_circuitbreaker_calls_seconds_count`、`..._not_permitted_calls_total` | 成功 / 失败 / 被拒绝的流量 |
| 调用耗时 P99 | `resilience4j_circuitbreaker_calls_seconds_bucket` | 需要在 application.yml 中开启 percentiles-histogram |
| 慢调用比例 | `resilience4j_circuitbreaker_slow_call_rate` | 下游变慢的早期信号 |

## 代码导读

1. [application.yml](src/main/resources/application.yml)：Actuator 暴露的端点、公共标签、直方图
2. [TrafficGenerator](src/main/java/com/kai/learn/cbdemo/l4/observability/TrafficGenerator.java)：持续产生流量
3. [docker-compose.yml](../../docker-compose.yml)、[monitoring/](../../monitoring)：Prometheus 采集配置、告警规则、Grafana 的数据源与看板

## 原理要点

- 告警规则（[alert-rules.yml](../../monitoring/alert-rules.yml)）：
  - `CircuitBreakerOpen`：打开超过 30 秒，说明下游持续故障
  - `CircuitBreakerFlapping`：5 分钟内状态变化超过 4 次，说明阈值过于敏感，或者下游处于半故障状态
  - `CircuitBreakerFailureRateHigh`：失败率接近阈值，提前预警
- 在 PromQL 中计算状态时，`state` 标签的取值不同，要先 `max by (name)` 去掉 `state` 标签再相加，否则向量无法匹配。

## 动手练习

1. 让下游进入 `FLAKY 50%` 模式，观察状态面板上的「抖动」，以及 Prometheus 中 `CircuitBreakerFlapping` 告警的变化。
2. 同时启动 L2-05（8205），它的指标也会被采集；在看板顶部的 application 下拉框中切换应用。
3. 为 Spring Cloud 的 Observation 指标（`spring_cloud_circuitbreaker_*`）加一个面板，对比它与 Resilience4j 指标的区别。

## 思考题

<details>
<summary>1. 熔断器打开了就一定要告警吗？</summary>

短暂的打开（几秒钟就恢复）往往是熔断器在正常工作，频繁告警会让人麻木。
更合理的做法是告警「持续打开」（`for: 30s`）和「频繁抖动」，把短暂的打开作为看板上的信息展示。
</details>
