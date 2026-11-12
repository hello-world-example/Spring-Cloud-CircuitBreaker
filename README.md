# Spring Cloud CircuitBreaker 学习项目

> 下游故障时快速失败、给下游恢复的时间，并防止故障沿调用链蔓延成雪崩。
>
> 版本：JDK 25 / Spring Boot 4.0.9 / Spring Cloud 2025.1.4（CircuitBreaker 5.0.3，Resilience4j 2.3.0）/ Lombok 1.18.48
>
> 编译时 Lombok 会打印 `sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit` 警告（JDK 24+），不影响结果。
> IDE 中需要安装 Lombok 插件（IntelliJ IDEA 已内置）。

## 快速开始

```bash
# 1. 构建（第一次运行任何模块之前都要先执行，会把 cbdemo-common 安装到本地仓库）
./mvnw install -DskipTests

# 2. 终端 1：启动模拟下游（8081），所有 L1/L2/L4 模块都调用它
./mvnw -pl cbdemo-downstream spring-boot:run

# 3. 终端 2：启动第一个模块
./mvnw -pl level1-basic/01-circuitbreaker-factory spring-boot:run

# 4. 让下游出故障，再连续调用 15 次
curl -X POST 'localhost:8081/chaos?mode=ERROR'
curl 'localhost:8101/demo/burst?n=15'
```

预期结果：前几次 `source=fallback`，失败原因是 HTTP 500；之后 `state` 变为 `OPEN`，`costMs≈0`，失败原因是 `CallNotPermittedException`。
这时下游一个请求都收不到了（`curl localhost:8081/chaos` 中的 `receivedCount` 不再增长）。

L3 和 L4-01 不需要启动任何服务，直接运行测试：`./mvnw -pl level3-principle/01-mini-circuitbreaker test`。

## 学习路线

```mermaid
flowchart LR
  L1[L1 入门<br/>怎么用] --> L2[L2 进阶<br/>怎么集成] --> L3[L3 原理<br/>为什么] --> L4[L4 生产<br/>怎么落地]
```

| 级别 | 模块 | 学习目标 | 用时 |
|---|---|---|---|
| L1 | [01-circuitbreaker-factory](level1-basic/01-circuitbreaker-factory/) | `run(supplier, fallback)`；观察 CLOSED → OPEN → HALF_OPEN → CLOSED | 30min |
| L1 | [02-resilience4j-config](level1-basic/02-resilience4j-config/) | 关键参数；YAML 与 Customizer 的**优先级**；慢调用熔断 | 45min |
| L1 | [03-timelimiter-fallback](level1-basic/03-timelimiter-fallback/) | 超时的两层含义；按异常类型降级；4xx 不计入失败 | 45min |
| L2 | [01-reactive-circuitbreaker](level2-advanced/01-reactive-circuitbreaker/) | WebClient + 响应式熔断；用操作符实现超时 | 30min |
| L2 | [02-bulkhead](level2-advanced/02-bulkhead/) | 线程池舱壁 vs 信号量舱壁；**一次调用两个线程** | 45min |
| L2 | [03-openfeign-circuitbreaker](level2-advanced/03-openfeign-circuitbreaker/) | Feign 自动熔断；熔断器命名与按 group 配置 | 30min |
| L2 | [04-gateway-circuitbreaker](level2-advanced/04-gateway-circuitbreaker/) | 网关路由熔断；`statusCodes`；降级端点 | 30min |
| L2 | [05-events-metrics](level2-advanced/05-events-metrics/) | 事件监听与告警；Micrometer 指标；Boot 4 兼容性 | 30min |
| L2 | [06-switch-implementation](level2-advanced/06-switch-implementation/) | 三种实现的**行为差异**（实测 Spring Retry 不会打开） | 45min |
| L2 | [07-http-interface-circuitbreaker](level2-advanced/07-http-interface-circuitbreaker/) | Boot 4 HTTP Service Client + 熔断（2025.x 新特性） | 30min |
| L3 | [01-mini-circuitbreaker](level3-principle/01-mini-circuitbreaker/) | **手写熔断器**，与 Resilience4j 对照测试 | 1h |
| L3 | [02-sliding-window](level3-principle/02-sliding-window/) | 手写时间窗口；两种窗口各自的失效场景 | 45min |
| L3 | [03-factory-source](level3-principle/03-factory-source/) | 源码导读；**线程模型与上下文丢失**；disable-thread-pool 的代价 | 1h |
| L3 | [04-decorator-order](level3-principle/04-decorator-order/) | Retry 与熔断的嵌套顺序 | 30min |
| L4 | [01-tuning-simulator](level4-production/01-tuning-simulator/) | 用模拟器评估参数：误熔断 vs 发现延迟 | 45min |
| L4 | [02-observability](level4-production/02-observability/) | Prometheus + Grafana 看板与告警规则 | 45min |
| L4 | [03-layered-protection](level4-production/03-layered-protection/) | 强弱依赖；慢的弱依赖怎样拖垮服务（6091ms → 316ms） | 45min |
| L4 | [04-ops-switch](level4-production/04-ops-switch/) | 强制熔断 / 关闭 / 只统计；动态调参 | 30min |

建议：每个模块先读 README 中的「学习目标」，运行看到效果，再按「代码导读」的顺序看代码，最后做「动手练习」。
代码中的关键位置用 `【原理】【坑】【对比】【源码】【生产】` 标记，可以在 IDE 中全局搜索。

## 项目结构

```
Spring-Cloud-CircuitBreaker/
├── pom.xml                    根 POM：聚合模块；import Boot 与 Cloud 两个 BOM 管理版本（不继承 Boot parent），统一构建配置
├── cbdemo-common/             公共代码：ApiResult（带 source 字段区分真实结果与降级）、CallRecord、
│                              DownstreamApi（下游 HTTP 客户端）、Errors（异常解包）、MutableClock（测试用时钟）
├── cbdemo-downstream/         模拟下游（8081）：运行时切换 NORMAL / SLOW / ERROR / FLAKY 故障模式
├── level1-basic/              L1 入门（81xx）
├── level2-advanced/           L2 进阶（82xx，网关 8080）
├── level3-principle/          L3 原理（只有测试，不需要启动服务）
├── level4-production/         L4 生产（84xx）
├── docs/                      原理文档
├── docker-compose.yml         Prometheus + Grafana（只有 L4-02 需要）
└── monitoring/                Prometheus 采集配置、告警规则、Grafana 数据源与看板
```

## 外部依赖与端口

| 服务 / 应用 | 端口 | 说明 |
|---|---|---|
| cbdemo-downstream | 8081 | 模拟下游，`GET/POST /chaos` 切换故障模式 |
| L1-01 ~ L1-03 | 8101 ~ 8103 | |
| L2-04 网关 | 8080 | |
| L2-01 ~ L2-07（除 04） | 8201 ~ 8207 | L2-06 的三个 app 共用 8206 |
| L4-02 ~ L4-04 | 8402 ~ 8404 | |
| Prometheus / Grafana | 9090 / 3000 | `docker compose up -d`，只有 L4-02 需要 |

下游故障模式：

```bash
curl localhost:8081/chaos                                   # 查看当前模式与收到的请求数
curl -X POST 'localhost:8081/chaos?mode=ERROR'              # /api/data 全部返回 500
curl -X POST 'localhost:8081/chaos?mode=SLOW&delayMs=2000'  # 每个请求延迟 2 秒
curl -X POST 'localhost:8081/chaos?mode=FLAKY&failRate=50'  # 50% 的概率返回 500
curl -X POST 'localhost:8081/chaos?mode=NORMAL'             # 恢复正常
```

## 原理文档

- [00-overview：容错模式全景、分层结构、与 Hystrix / Sentinel 对比](docs/00-overview.md)
- [01-state-machine：熔断器状态机](docs/01-state-machine.md)
- [02-sliding-window：滑动窗口](docs/02-sliding-window.md)
- [03-decorator-order：装饰器组合顺序](docs/03-decorator-order.md)
- [04-thread-and-context：线程模型与上下文传递](docs/04-thread-and-context.md)
- [05-production-tuning：参数、超时预算与落地流程](docs/05-production-tuning.md)
- [faq：常见坑与排查](docs/faq.md)

## 和你熟悉的技术对照

| Spring Cloud CircuitBreaker 概念 | 类似于 | 关键区别 |
|---|---|---|
| `CircuitBreakerFactory`（抽象） | SLF4J（接口）+ Logback（实现） | 抽象只统一了调用方式，**没有统一行为**（L2-06） |
| `Customizer<XxxFactory>` | `WebMvcConfigurer`、`RestTemplateCustomizer` | 容器中所有同类型的 Bean 依次应用到 Factory 上 |
| 熔断器 | Hystrix | Hystrix 已停止维护；Resilience4j 是函数式装饰器，隔离方式可以自由组合 |
| 熔断 + 流控 | Sentinel | Sentinel 的强项是流控和动态规则；Resilience4j 更轻量 |
| 舱壁 | 为每个下游配置独立的线程池（Dubbo 的线程池隔离） | Spring Cloud 中线程池舱壁一次调用占两个线程 |
| `run()` 中的线程切换 | `@Async` | 同样会丢失 ThreadLocal（traceId、事务、登录用户） |

## 学完之后

- 官方文档：https://docs.spring.io/spring-cloud-circuitbreaker/reference/
- Resilience4j 文档：https://resilience4j.readme.io/
- 继续学习：Sentinel（流控、热点参数、集群流控）；Micrometer context-propagation（上下文传递的标准方案）
