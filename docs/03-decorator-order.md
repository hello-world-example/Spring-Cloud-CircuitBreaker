# 03. 装饰器组合顺序

> 相关模块：[L2-02](../level2-advanced/02-bulkhead)、[L3-03](../level3-principle/03-factory-source)、[L3-04](../level3-principle/04-decorator-order)
> 读完能回答：多个容错组件组合时，谁在外层、谁在内层？不同顺序的后果是什么？

## 要解决的问题

Retry、CircuitBreaker、TimeLimiter、Bulkhead 都是「包一层」的装饰器，包装的顺序决定了它们彼此能看到什么。

## 三种场景下的顺序

**Resilience4j 注解方式（resilience4j-spring-boot）的默认顺序**

```
Retry( CircuitBreaker( RateLimiter( TimeLimiter( Bulkhead( 调用 ) ) ) ) )
```

**Spring Cloud CircuitBreaker（源码 `Resilience4JCircuitBreaker#run`）**

```
CircuitBreaker( Bulkhead( TimeLimiter( executor.submit(调用) ) ) )      ← 没有 Retry
```

**Spring Cloud 响应式版本（`ReactiveResilience4JCircuitBreaker#run`）**

```
toRun.transform(Bulkhead).transform(CircuitBreaker).timeout(...).onErrorResume(fallback)
即：Timeout( CircuitBreaker( Bulkhead( 调用 ) ) )                         ← 超时在熔断外层
```

## 关键的几对关系

| 组合 | 外层 → 内层 | 后果 |
|---|---|---|
| Retry / CircuitBreaker | Retry → CB | 熔断器看到每一次重试，失败被放大，打开得更早；熔断后要排除 CallNotPermittedException，否则会白白重试 |
| | CB → Retry | 熔断器把「重试全部失败」记为 1 次失败；熔断打开前下游承受 N 倍压力（L3-04：15 次 vs 5 次） |
| TimeLimiter / Retry | TimeLimiter → Retry | 超时是「总超时」 |
| | Retry → TimeLimiter | 超时是「每次尝试的超时」 |
| CircuitBreaker / Bulkhead | CB → Bulkhead | 舱壁拒绝（BulkheadFullException）会被熔断器计为失败，建议加入 ignore-exceptions |
| CircuitBreaker / TimeLimiter（响应式） | Timeout → CB | 超时时 CB 只收到取消信号，源码中要手动补一次 `cb.onError` |

## 设计取舍

- Retry 放在最外层是主流选择：熔断器更真实地反映下游状态，熔断打开后重试立即停止。前提是只对幂等操作重试。
- Bulkhead 放在最内层：限制的是「真正发出去的并发」，被熔断拒绝的请求不应该占用舱壁名额。

## 常见问题

<details>
<summary>Spring Cloud CircuitBreaker 想加重试怎么办？</summary>

抽象层没有重试。可以在 run() 外面再包一层 Resilience4j 的 Retry（Retry 在外层），或者使用 Spring Framework 7 的 `@Retryable`。
注意排除 CallNotPermittedException，并且只对幂等操作重试。
</details>
