# L1-01 CircuitBreakerFactory 入门

## 学习目标

- 怎样用 `CircuitBreakerFactory` 给一次远程调用加上熔断和降级？
- 熔断器的三个状态 CLOSED / OPEN / HALF_OPEN 分别在什么时候切换？
- 为什么说 OPEN 状态是「快速失败」？下游真的收不到请求了吗？
- `create(id)` 中的 id 决定了什么？

## 运行

```bash
# 终端 1：下游
./mvnw -pl cbdemo-downstream spring-boot:run
# 终端 2：本模块
./mvnw -pl level1-basic/01-circuitbreaker-factory spring-boot:run
```

然后按顺序执行 [requests.http](requests.http) 中的请求（IntelliJ 中可以直接点击运行；也可以用 curl）。

预期结果（第 3 步，下游全部返回 500 时的 burst）：

```
seq source    state  costMs  message
1   fallback  CLOSED 8       InternalServerError: 500 ...
2   fallback  CLOSED 4       InternalServerError: 500 ...
3   fallback  OPEN   6       InternalServerError: 500 ...     ← 失败率达到 50%，切换为 OPEN
4   fallback  OPEN   0       CallNotPermittedException ...    ← 不再调用下游，0ms 直接返回
...
```

第 4 步 `GET /chaos` 的 `receivedCount` 等于真实失败的次数：OPEN 状态的请求一个都没有发到下游。

第 6 步等待 5 秒后再调用：`HALF_OPEN → HALF_OPEN → CLOSED`（3 个探测请求中，前 2 个状态还是 HALF_OPEN，第 3 个成功后切换为 CLOSED）。

## 代码导读

1. [DownstreamClient](src/main/java/com/kai/learn/cbdemo/l1/factory/DownstreamClient.java)：普通的 HTTP 调用，没有任何熔断逻辑
2. [ProductService](src/main/java/com/kai/learn/cbdemo/l1/factory/ProductService.java)：**核心**，`circuitBreaker.run(supplier, fallback)`
3. [DemoController](src/main/java/com/kai/learn/cbdemo/l1/factory/DemoController.java)：观察接口，从 Resilience4j 的 Registry 中读取状态
4. [application.yml](src/main/resources/application.yml)：调小窗口，便于观察

## 原理要点

- 熔断器是一个**装饰器**：`run()` 包在任意一个 `Supplier` 外面，根据调用结果（是否抛出异常）更新滑动窗口中的统计数据。
- **失败率 = 窗口中失败的次数 / 窗口中的调用次数**。调用次数少于 `minimum-number-of-calls` 时不计算失败率。
- OPEN 状态下，`run()` 直接抛出 `CallNotPermittedException` 并进入降级，耗时几乎为 0，这就是「快速失败」，可以保护调用方的线程。
- 默认配置下，OPEN 等待结束后**不会自动**切换为 HALF_OPEN，而是由「下一次调用」触发切换。
- 完整的状态机说明见 [docs/01-state-machine.md](../../docs/01-state-machine.md)。

## 动手练习

1. 把 `failure-rate-threshold` 改为 80，重新执行第 2~3 步，OPEN 出现得更晚了吗？为什么？
2. 用 `POST /chaos?mode=FLAKY&failRate=30` 让下游随机失败，多次执行 `/demo/burst?n=30`，熔断器会打开吗？
3. 在 `ProductService` 中 catch 住 `client.getData()` 的异常，返回一个错误对象（不抛出异常），再执行第 3 步，会发生什么？

## 思考题

<details>
<summary>1. 熔断器的 id 应该按什么粒度划分？按服务、按接口，还是按实例（IP）？</summary>

一般按「下游服务」或「下游接口」划分。粒度太粗：一个慢接口会把同一服务的其他健康接口一起熔断；粒度太细：每个熔断器的样本太少，失败率波动大，Registry 中的对象也多。
按实例（IP）划分适用于「下游部分实例故障」的场景，但通常更适合交给负载均衡层（比如异常实例摘除 / outlier detection）来做。
</details>

<details>
<summary>2. 熔断和降级是一回事吗？</summary>

不是。熔断是一种**判断机制**（什么时候不再调用下游），降级是一种**处理策略**（调用不了下游时返回什么）。
熔断一定会触发降级，但降级还有很多其他的触发原因：超时、异常、限流、手动开关（见 L4-04）。
</details>
