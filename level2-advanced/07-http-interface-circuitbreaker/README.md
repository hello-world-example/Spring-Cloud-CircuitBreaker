# L2-07 HTTP Interface 集成熔断

## 学习目标

- Spring Boot 4 的 HTTP Service Client（`@ImportHttpServices`）怎样使用？
- Spring Cloud 是怎样给 HTTP Interface 自动加上熔断的？熔断器的 id 是什么？
- `@HttpServiceFallback` 的降级方法是怎样匹配的？
- 和 OpenFeign 相比有什么区别？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level2-advanced/07-http-interface-circuitbreaker spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

```
/demo/breakers → ["downstream = CLOSED"]      ← 熔断器 id = 组名

/demo/burst（下游 ERROR，之前已有 3 次成功）
1  fallback  InternalServerError: 500 ...        CLOSED
2  fallback  InternalServerError: 500 ...        CLOSED
3  fallback  InternalServerError: 500 ...        OPEN
4  fallback  CallNotPermittedException ...       OPEN
```

降级结果的 message 中有失败原因，说明匹配到的是 `data(Throwable, String)` 这个降级方法。

## 代码导读

1. [HttpInterfaceApplication](src/main/java/com/kai/learn/cbdemo/l2/httpinterface/HttpInterfaceApplication.java)：`@ImportHttpServices` 与 `@HttpServiceFallback`，以及**整体原理**
2. [DownstreamHttpApi](src/main/java/com/kai/learn/cbdemo/l2/httpinterface/DownstreamHttpApi.java)：声明式接口
3. [DownstreamHttpApiFallbacks](src/main/java/com/kai/learn/cbdemo/l2/httpinterface/DownstreamHttpApiFallbacks.java)：降级方法的匹配规则
4. [application.yml](src/main/resources/application.yml)：`spring.http.serviceclient.<group>.*`

## 原理要点

- `CircuitBreakerRestClientHttpServiceGroupConfigurer`（spring-cloud-commons）为每个组装配 `CircuitBreakerAdapterDecorator`，
  组内所有方法的调用都被包进 `circuitBreakerFactory.create(组名).run(...)` 中。
- 降级匹配：同名同参数的方法优先；其次是第一个参数为 `Throwable` 的方法；都没有就抛出 `NoFallbackAvailableException`。
- `@HttpServiceFallback` 必须标注在 Spring Bean 上；降级类本身由框架反射实例化，不是 Bean。

| 对比 | OpenFeign（L2-03） | HTTP Interface（本模块） |
|---|---|---|
| 依赖 | spring-cloud-openfeign（维护模式） | Spring Framework 自带 |
| 熔断器粒度 | 每个方法一个 | 每个组一个 |
| 降级 | Spring Bean（fallback / fallbackFactory） | 反射实例化的普通类，按方法签名匹配 |
| 开关 | `spring.cloud.openfeign.circuitbreaker.enabled`（默认 false） | `spring.cloud.circuitbreaker.http-services.enabled`（默认 true） |

## 动手练习

1. 在降级类中再加一个 `data(String caller)` 方法，降级结果会变成哪一个？为什么？
2. 新建第二个接口和第二个组（比如 `slow-group`），让慢接口使用独立的熔断器。

## 思考题

<details>
<summary>1. 熔断器按「组」划分，会有什么问题？怎么解决？</summary>

同组的所有方法共享一个失败率：一个接口持续失败，会连累同组的其他接口一起被熔断。
解决办法是按「容错特征」划分组：把慢接口、不重要的接口放到单独的组中。
</details>
