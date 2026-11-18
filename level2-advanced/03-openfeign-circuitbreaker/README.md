·# L2-03 OpenFeign 集成熔断

## 学习目标

- 怎样让 Feign 客户端的每个方法自动拥有熔断和降级能力？
- Feign 自动创建的熔断器叫什么名字？怎样针对它写配置？
- `fallback` 和 `fallbackFactory` 有什么区别？
- Feign 超时和 TimeLimiter 超时，谁先触发？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/03-openfeign-circuitbreaker spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

```
/demo/breakers
{"DownstreamFeignClientdataString": {"state": "CLOSED", "tags": {"group": "downstream"}, "failureRateThreshold": 40.0}}
   ↑ 熔断器名称 = 类名 + 方法名 + 参数类型；group 标签来自 @FeignClient 的 name；40 来自 configs.downstream

/demo/burst（下游 ERROR，之前已有 3 次成功）
1  fallback  InternalServerError: [500] during [GET] ...   CLOSED
2  fallback  InternalServerError: [500] during [GET] ...   OPEN    ← 3 成功 + 2 失败 = 40%
3  fallback  CallNotPermittedException ...                 OPEN

/demo/slow?delayMs=1500
→ RetryableException: Read timed out，costMs=1006   ← Feign 读超时（1s）先于 TimeLimiter（2s）触发
```

## 代码导读

1. [application.yml](src/main/resources/application.yml)：`spring.cloud.openfeign.circuitbreaker.*` 和按 group 的配置
2. [DownstreamFeignClient](src/main/java/com/kai/learn/cbdemo/l2/feign/DownstreamFeignClient.java)：调用链与熔断器命名
3. [DownstreamFallbackFactory](src/main/java/com/kai/learn/cbdemo/l2/feign/DownstreamFallbackFactory.java)：能拿到失败原因的降级
4. [FeignDemoController](src/main/java/com/kai/learn/cbdemo/l2/feign/FeignDemoController.java)

## 原理要点

- `FeignCircuitBreakerInvocationHandler` 在 JDK 动态代理中把每次方法调用包进 `circuitBreakerFactory.create(name, group).run(...)`。
- 熔断器名称由 `CircuitBreakerNameResolver` 生成，可以注册自己的 Bean 来改变命名规则。
- 开启 `group.enabled` 后，配置可以按客户端统一写在 `configs.<feign-client-name>` 中。
- Feign 熔断同样运行在 Factory 的线程池中，同样受 TimeLimiter 的约束（默认 1s！）。

## 动手练习

1. 关闭 `group.enabled`，再看 `/demo/breakers`，failureRateThreshold 变成多少？怎样再针对这个方法单独配置？
2. 注册一个 `CircuitBreakerNameResolver` Bean，让熔断器名称变为 `feign-downstream-data`。
3. 把 Feign 的 `read-timeout` 改为 5000，再访问 `/demo/slow?delayMs=3000`，这次是谁先超时？

## 思考题

<details>
<summary>1. 按「方法」划分熔断器好，还是按「客户端」划分好？</summary>

按方法划分更精细：一个慢接口不会影响同一服务的其他接口；但样本更分散，失败率波动更大。
按客户端划分（L2-07 中 HTTP Interface 的做法）更简单、样本更多，但一个坏接口可能连累整个服务。
可以按方法划分熔断器，再按客户端（group）共享配置，这正是本模块的做法。
</details>
