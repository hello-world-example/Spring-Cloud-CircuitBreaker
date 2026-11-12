# L2-04 网关熔断

## 学习目标

- 怎样在 Spring Cloud Gateway 中为一条路由配置熔断和降级？
- 下游返回 500 时，网关的熔断器为什么默认感知不到？
- 降级端点怎样拿到失败原因，并返回不同的状态码？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/04-gateway-circuitbreaker spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

```
下游 ERROR 时连续访问 /ds/api/data：
502 gateway-fallback CircuitBreakerStatusCodeException: 500 INTERNAL_SERVER_ERROR   {breakerState: CLOSED}
502 ...                                                                              {breakerState: CLOSED}
502 ...                                                                              {breakerState: CLOSED}
502 ...                                                                              {breakerState: OPEN}
503 gateway-fallback CallNotPermittedException ...                                   {breakerState: OPEN}

/ds/api/slow?delayMs=2000
504 gateway-fallback TimeoutException: Did not observe any item ... within 1000ms
```

## 代码导读

1. [pom.xml](pom.xml)：Spring Cloud 2025.x 中网关 starter 的新名称
2. [application.yml](src/main/resources/application.yml)：**核心**，路由、`CircuitBreaker` 过滤器、`statusCodes`
3. [FallbackController](src/main/java/com/kai/learn/cbdemo/l2/gateway/FallbackController.java)：降级端点

## 原理要点

- CircuitBreaker 过滤器 = `reactiveCircuitBreakerFactory.create(name).run(chain.filter(exchange), fallback)`，
  把「转发到下游」这整个过程包进熔断器中。
- 下游返回 500 时，转发本身是「成功」的，不会抛出异常。`statusCodes` 让过滤器把这些状态码转换为
  `CircuitBreakerStatusCodeException`，熔断器才能统计到。
- 失败原因保存在 `ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR` 中。
- `fallbackUri` 只支持 `forward:`，即网关内部转发。

## 动手练习

1. 删掉 `statusCodes`，下游 ERROR 时连续访问 10 次，熔断器还会打开吗？客户端收到的是什么？
2. 再配置一条路由，让 `forward:/fallback/downstream` 转发到另一个「备用服务」（可以就用下游的 `/api/slow?delayMs=0`）。

## 思考题

<details>
<summary>1. 网关层熔断和服务层熔断应该怎么分工？</summary>

网关层按「路由 / 后端服务」做粗粒度的保护，防止整个后端服务故障时请求堆积在网关；
服务层按「依赖」做细粒度的保护，并且能做业务相关的降级（返回缓存数据、默认值）。
两层的超时要层层递减：网关超时 > 服务内部调用链的总超时。详见 [L4-03](../../level4-production/03-layered-protection/README.md)。
</details>
