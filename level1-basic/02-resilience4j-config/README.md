# L1-02 Resilience4j 配置与优先级

## 学习目标

- 熔断器有哪些关键参数？各自影响什么？
- YAML（`configs` / `instances`）和代码（`Customizer`）两种配置方式，同时存在时以谁为准？
- `create(id, group)` 中的 group 有什么用？
- 什么是「慢调用熔断」？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level1-basic/02-resilience4j-config spring-boot:run
```

执行 [requests.http](requests.http)。第 1 步 `GET /demo/config` 的实测结果：

| id | group | failureRateThreshold | 生效的配置来源 |
|---|---|---|---|
| plain | - | **60** | `Customizer.configureDefault`（覆盖了 YAML `configs.default` 中的 50） |
| customized | - | **30** | `Customizer.configure(..., "customized")` |
| yaml-instance | - | **80** | YAML `instances`（Customizer 中为它配置的 30 **没有生效**） |
| order-api | strict | **20** | YAML `configs.strict`（通过 group 匹配） |
| slow-call | - | 50（另有慢调用阈值 500ms / 50%） | YAML `instances` |

第 3 步慢调用实验（下游每次都成功，但耗时 800ms）：

```
seq source      state   costMs
1   downstream  CLOSED  887
2   downstream  CLOSED  812
3   downstream  OPEN    820     ← 慢调用比例达到阈值，即使全部成功也会熔断
4   fallback    OPEN    0
```

> 注意：`/demo/config` 为了读取配置会先空跑一次（记为一次成功的快速调用），所以第几次切换为 OPEN 会受之前请求的影响。

## 代码导读

1. [application.yml](src/main/resources/application.yml)：`configs` 和 `instances` 的区别，各参数的说明
2. [CircuitBreakerCustomizerConfig](src/main/java/com/kai/learn/cbdemo/l1/config/CircuitBreakerCustomizerConfig.java)：代码配置，以及**优先级总结**
3. [ConfigDemoController](src/main/java/com/kai/learn/cbdemo/l1/config/ConfigDemoController.java)：读取生效配置；`create(id)` 与 `create(id, group)` 的区别

## 原理要点

优先级来自源码 `Resilience4JCircuitBreakerFactory#create` 与 `Resilience4JCircuitBreaker#run`：

```
1. YAML instances.<id>        启动时就创建了实例；run() 中 registry.circuitBreaker(id, config) 发现实例已存在，
                              直接返回，代码中算出的 config 被忽略
2. YAML configs.<id>          registry.getConfiguration(id)
3. YAML configs.<group>       registry.getConfiguration(group)
4. factory.configure(..., id) Customizer 针对 id 的配置
5. factory.configureDefault   Customizer 的全局默认配置；没有配置它时，使用 YAML configs.default
```

关键参数速查：

| 参数 | 默认值 | 作用 |
|---|---|---|
| `sliding-window-type` | COUNT_BASED | 按次数或按时间统计 |
| `sliding-window-size` | 100 | 窗口大小（次数或秒数） |
| `minimum-number-of-calls` | 100 | 样本量不足时不做判断 |
| `failure-rate-threshold` | 50 | 失败率阈值（%） |
| `slow-call-duration-threshold` | 60s | 超过这个耗时算慢调用 |
| `slow-call-rate-threshold` | 100 | 慢调用比例阈值（%） |
| `wait-duration-in-open-state` | 60s | OPEN 状态持续的时间 |
| `permitted-number-of-calls-in-half-open-state` | 10 | HALF_OPEN 状态的探测请求数 |
| `automatic-transition-from-open-to-half-open-enabled` | false | 是否到点自动切换为 HALF_OPEN |

## 动手练习

1. 注释掉 `defaultCustomizer` Bean，重启后再看 `plain` 的配置，验证第 5 条优先级。
2. 在 YAML 中加上 `configs.customized.failure-rate-threshold: 90`，`customized` 会变成多少？对照优先级表解释原因。
3. 把 `slow-call` 的 `sliding-window-type` 改为 COUNT_BASED，用 `intervalMs=2000` 慢慢调用，对比两种窗口的表现。

## 思考题

<details>
<summary>1. 为什么 YAML instances 的优先级最高？这种设计有什么隐患？</summary>

因为 resilience4j-spring-boot4 在启动时就把 instances 中的熔断器实例创建出来，放进了 Registry，而 Spring Cloud 在 run() 时调用的是「有则返回，没有才创建」的 `registry.circuitBreaker(id, config)`。
隐患：同一个 id 在 Customizer 中写的配置会被**静默忽略**，不会有任何报错。团队中应该约定只用一种配置方式。
</details>

<details>
<summary>2. 慢调用阈值应该怎么定？</summary>

参考下游接口的 P99 延迟，再结合调用方自身的超时预算（比如网关总超时 3s，调用链上有 3 跳）。慢调用阈值通常小于 TimeLimiter 超时，这样在「还没超时但已经变慢」时就能提前熔断。详见 [docs/05-production-tuning.md](../../docs/05-production-tuning.md)。
</details>
