# L1-03 超时控制与分类降级

## 学习目标

- TimeLimiter 是怎样实现超时的？为什么业务代码会跑在另一个线程中？
- TimeLimiter 超时之后，后台的 HTTP 请求会停止吗？下游还会继续处理吗？
- 降级时为什么要按异常类型区别对待？4xx 应该触发熔断吗？
- 不提供降级函数时会怎样？

## 运行

```bash
./mvnw -pl cbdemo-downstream spring-boot:run
./mvnw -pl level1-basic/03-timelimiter-fallback spring-boot:run
```

执行 [requests.http](requests.http)。实测结果：

**超时**（`/demo/timeout?delayMs=1000`，TimeLimiter 超时时间为 500ms）：

```json
{"result": {"source": "fallback", "message": "调用超时，结果未知（写操作要做幂等或查询确认）"},
 "costMs": 508, "callerThread": "http-nio-8103-exec-7"}
```

控制台日志（注意线程名是 `pool-2-thread-1`，不是 Tomcat 线程）：

```
[pool-2-thread-1] 后台调用在 512ms 后结束：ResourceAccessException: ... Request was interrupted
```

**4xx 与 5xx**：

```
/demo/status/400?n=6  → 6 次都是 client-error，state 一直是 CLOSED（ignore-exceptions 生效）
/demo/status/500?n=8  → 前 5 次「下游故障：HTTP 500」，第 5 次后变为 OPEN，之后是「熔断中，快速失败」
```

**没有降级函数**：抛出 `NoFallbackAvailableException`，原始的 503 异常在 `getCause()` 中。

## 代码导读

1. [TimeoutApplication](src/main/java/com/kai/learn/cbdemo/l1/timeout/TimeoutApplication.java)：HTTP 客户端超时与 TimeLimiter 超时，两层超时的区别
2. [application.yml](src/main/resources/application.yml)：`ignore-exceptions`、`timelimiter.instances`
3. [FallbackResolver](src/main/java/com/kai/learn/cbdemo/l1/timeout/FallbackResolver.java)：**按异常类型降级**
4. [TimeoutDemoController](src/main/java/com/kai/learn/cbdemo/l1/timeout/TimeoutDemoController.java)：三个实验

## 原理要点

- `Resilience4JCircuitBreakerFactory` 内部默认有一个 `Executors.newCachedThreadPool()`，`run()` 会把 Supplier
  提交到这个线程池中，再用 `TimeLimiter.decorateFutureSupplier` 等待 `Future.get(timeout)`。
  所以**默认情况下业务代码总是在另一个线程中执行**，ThreadLocal（traceId、登录用户、事务）都会丢失，见 L3-03。
- TimeLimiter 超时后会调用 `future.cancel(true)`，也就是中断工作线程。HTTP 客户端是否响应中断决定了后台线程能否及时释放；
  而下游**已经收到了请求**，无论如何都会继续处理。
- 超时意味着**结果未知**：对于写操作，降级成「失败」可能导致重复提交。
- `ignore-exceptions` 中的异常不参与统计；`record-exceptions` 是白名单，配置后只有这些异常计为失败。

## 动手练习

1. 把 `TimeoutApplication` 中的请求工厂改为 `new SimpleClientHttpRequestFactory()`，再访问 `/demo/timeout?delayMs=1000`，
   后台线程是在 500ms 结束，还是在 1000ms 结束？
2. 把 `fast-timeout` 的 `cancel-running-future` 改为 false，后台线程会在什么时候结束？
3. 在 YAML 中设置 `spring.cloud.circuitbreaker.resilience4j.disable-time-limiter: true`，超时实验还会降级吗？

## 思考题

<details>
<summary>1. 超时时间应该怎么设置？HTTP 读超时和 TimeLimiter 超时谁应该更大？</summary>

HTTP 读超时 ≤ TimeLimiter 超时。TimeLimiter 只负责「调用方不再等待」，真正释放连接和线程靠的是 HTTP 客户端超时。
如果 HTTP 读超时更大，TimeLimiter 触发之后，后台线程和连接还会被占用一段时间，高并发下会耗尽线程池。
整体上按「超时预算」从外向内递减：网关 > 服务 A 的总超时 > A 调用 B 的超时。
</details>

<details>
<summary>2. 降级时可以直接返回 null 或空集合吗？</summary>

要看调用方是否能区分「真的没有数据」和「降级了」。比如推荐列表降级成空列表，用户看到的只是没有推荐，这是可以接受的；
但库存查询降级成 0，可能导致用户无法下单，甚至被误判为售罄。通常要返回明确的降级标识（本项目中的 `source=fallback`），由上层决定如何展示。
</details>
