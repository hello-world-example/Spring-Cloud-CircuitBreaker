# L3-04 装饰器组合顺序

## 学习目标

- 多个容错组件（Retry、CircuitBreaker、Bulkhead、TimeLimiter、RateLimiter）组合时，顺序为什么重要？
- Retry 放在熔断器的外层和内层，各有什么后果？
- Resilience4j 注解方式的默认顺序是什么？

## 运行

```bash
./mvnw -pl level3-principle/04-decorator-order test
```

实测输出：

```
下游持续故障，发起 20 个请求：
Retry 在外层 Retry(CB(x))     下游实际被调用   5 次，第  2 个请求时熔断打开
Retry 在内层 CB(Retry(x))     下游实际被调用  15 次，第  5 个请求时熔断打开
```

## 代码导读

1. [DecoratorOrderTest](src/test/java/com/kai/learn/cbdemo/l3/order/DecoratorOrderTest.java)：**核心**，两种顺序的对比

## 原理要点

- `Decorators.ofSupplier(x).withA().withB()`：先 with 的在内层，得到 `B(A(x))`。
- **Retry 在外层**：一次请求最多 3 次经过熔断器，熔断器看到的失败被放大，所以打开得更早；
  熔断打开后，必须用 `ignoreExceptions(CallNotPermittedException.class)` 避免对「熔断中」做无意义的重试。
- **Retry 在内层**：熔断器把「3 次重试都失败」记为 1 次失败；在熔断打开之前，下游承受 3 倍的压力。
- Resilience4j 注解方式的默认顺序：`Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( 调用 ) ) ) ) )`。
- Spring Cloud CircuitBreaker 中没有 Retry，组合顺序固定为 `CircuitBreaker( Bulkhead( TimeLimiter( 调用 ) ) )`（L3-03）。
- 详见 [docs/03-decorator-order.md](../../docs/03-decorator-order.md)。

## 动手练习

1. 去掉 `ignoreExceptions(CallNotPermittedException.class)`，Retry 在外层时下游调用次数会变吗？请求耗时呢？
2. 再加一个 `TimeLimiter`，分别放在 Retry 的内层和外层：一个是「每次尝试的超时」，一个是「总超时」。

## 思考题

<details>
<summary>1. 生产环境中应该选哪种顺序？</summary>

通常选 Retry 在外层（也是 Resilience4j 的默认顺序）：熔断器能更真实地反映下游的失败率，熔断打开后重试立即停止，对下游的保护更好。
前提是重试只针对幂等操作，并且排除掉 CallNotPermittedException。
</details>
