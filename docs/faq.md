# 常见坑与排查

每一条都标注了出处（在哪个模块中可以复现），代码中对应的位置用 `【坑】` 标记，可以全局搜索。

## 配置

| 现象 | 原因 | 出处 |
|---|---|---|
| 超过 1 秒的正常调用也走了降级，降级原因是 TimeoutException | TimeLimiter 默认超时只有 **1 秒**。启动日志中有 `No timeLimiterConfig found for 'xxx'` 的警告 | L1-01 |
| Customizer 中为某个 id 写的配置不生效 | 同一个 id 在 YAML 的 `instances` 中也配置了，instances 优先级最高，且不会有任何提示 | L1-02 |
| YAML 中的 `configs.default` 不生效 | 代码中配置了 `configureDefault`，它会替换默认配置 | L1-02 |
| 启动报错或 NoSuchMethodError | Spring Boot 与 Spring Cloud 版本不配套 | 根 pom.xml |
| 运行时报 `parameter name information not available via reflection` | 不继承 Boot parent 时，编译器没有开启 `-parameters` | 根 pom.xml |
| `java -jar` 报 `no main manifest attribute` | 不继承 Boot parent 时，spring-boot-maven-plugin 没有声明 repackage 执行 | 根 pom.xml |
| 网关 starter 找不到 / 配置不生效 | 2025.x 中改名为 `spring-cloud-starter-gateway-server-webflux`，配置前缀为 `spring.cloud.gateway.server.webflux` | L2-04 |
| 引入了舱壁的配置但没有效果 | 没有引入 `resilience4j-bulkhead` 依赖 | L2-02 |

## 行为

| 现象 | 原因 | 出处 |
|---|---|---|
| 下游返回 4xx 也触发了熔断 | 没有配置 `ignore-exceptions` | L1-03 |
| 自己 catch 了异常返回错误对象，熔断器从不打开 | 熔断器只能感知异常 | L1-01 |
| 网关后端返回 500，熔断器不统计 | 网关转发 500 不算异常，需要配置 `statusCodes` | L2-04 |
| 等待时间早就过了，状态还是 OPEN | 默认由下一次调用触发切换；没有流量时不会切换 | L1-02 |
| 不提供降级函数时，异常类型变了 | 被包装成 `NoFallbackAvailableException`，原始异常在 `getCause()` | L1-03 |
| 降级中按异常类型判断失效 | 异常可能被包装成 ExecutionException / CompletionException，先 unwrap | common/Errors |
| Spring Retry 实现的熔断器从不打开 | 每次降级都会清除 RetryContext，失败计数无法累积 | L2-06 |
| 舱壁拒绝把熔断器打开了 | BulkheadFullException 默认计为失败 | L2-02 |

## 线程与上下文

| 现象 | 原因 | 出处 |
|---|---|---|
| run() 中的日志没有 traceId，拿不到登录用户 | 业务代码运行在 Factory 的线程池中 | L3-03 |
| 修复了 create(id) 的上下文，create(id, group) 仍然丢失 | 两个方法使用不同的线程池，都要替换 | L3-03 |
| 开启 disable-thread-pool 后超时配置失效 | 不切换线程就没有 Future，TimeLimiter 无法工作 | L3-03 |
| TimeLimiter 超时后，后台线程仍然占用很久 | HTTP 客户端不响应中断（如 HttpURLConnection），需要设置 HTTP 读超时 | L1-03 |
| 线程池舱壁下线程数翻倍 | 舱壁线程等待 Factory 线程池，一次调用两个线程 | L2-02 |

## 监控

| 现象 | 原因 | 出处 |
|---|---|---|
| `/actuator/health` 中没有 circuitBreakers | resilience4j-spring-boot3 的健康检查基于 Boot 3 的 API，Boot 4 下没有生效 | L2-05 |
| 动态调参后事件监听失效 | 替换了熔断器实例，旧实例上的监听器不会转移 | L4-04 |
| PromQL 中两个 state 指标相加结果为空 | `state` 标签不同，先 `max by (name)` 再相加 | L4-02 |

## 运行本项目时

| 现象 | 原因 |
|---|---|
| `./mvnw -pl xxx spring-boot:run` 报找不到 cbdemo-common | 先在根目录执行一次 `./mvnw install -DskipTests` |
| 示例应用运行中重新 `mvn install` 后，请求卡住或报 ClassNotFoundException | `java -jar` 运行的 jar 被覆盖了。重新打包前先停止应用，或者用 `spring-boot:run` 运行 |
| 端口被占用 | 端口表见根目录 README |
