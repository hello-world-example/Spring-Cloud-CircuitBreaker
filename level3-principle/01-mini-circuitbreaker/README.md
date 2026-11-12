# L3-01 手写熔断器

## 学习目标

- 熔断器的状态机、滑动窗口用不到 200 行代码怎么实现？
- 等待时间到了之后，状态是自己变成 HALF_OPEN 的吗？
- HALF_OPEN 状态下，是收到第一个失败就打开，还是等所有探测结果都回来再判断？
- 怎样证明手写版与 Resilience4j 的行为一致？

## 运行

不需要启动任何服务。

```bash
./mvnw -q -pl level3-principle/01-mini-circuitbreaker compile exec:java \
  -Dexec.mainClass=com.kai.learn.cbdemo.l3.mini.MiniCircuitBreakerDemo
./mvnw -pl level3-principle/01-mini-circuitbreaker test
```

`MiniCircuitBreakerDemo` 的输出：

```
== 阶段 1：下游故障，连续失败
#1  失败：下游 500   state=CLOSED    failureRate=-1.0     ← 样本不足 5 次，不计算失败率
...
    >>> 状态切换：CLOSED -> OPEN
#5  失败：下游 500   state=OPEN
#6  被拒绝（快速失败）state=OPEN
== 阶段 2：等待 5 秒（拨动时钟），下游恢复
    >>> 状态切换：OPEN -> HALF_OPEN                      ← 由第 9 次调用触发
#9  ok              state=HALF_OPEN
#10 ok              state=HALF_OPEN
    >>> 状态切换：HALF_OPEN -> CLOSED                    ← 3 个探测请求都成功
#11 ok              state=CLOSED
```

测试：`MiniCircuitBreakerTest` 8 个、`CompareWithResilience4jTest` 5 个（5 个随机种子 × 2000 步），全部通过。

## 代码导读

1. [MiniCircuitBreakerConfig](src/main/java/com/kai/learn/cbdemo/l3/mini/MiniCircuitBreakerConfig.java)：5 个核心参数
2. [CountBasedSlidingWindow](src/main/java/com/kai/learn/cbdemo/l3/mini/CountBasedSlidingWindow.java)：环形缓冲区 + O(1) 增量统计
3. [MiniCircuitBreaker](src/main/java/com/kai/learn/cbdemo/l3/mini/MiniCircuitBreaker.java)：**核心**，状态机（sealed interface + record + switch 模式匹配）
4. [MiniCircuitBreakerTest](src/test/java/com/kai/learn/cbdemo/l3/mini/MiniCircuitBreakerTest.java)：每个测试方法名就是一条状态机规则
5. [CompareWithResilience4jTest](src/test/java/com/kai/learn/cbdemo/l3/mini/CompareWithResilience4jTest.java)：对照测试

## 原理要点

- 每个状态各自持有数据（CLOSED：滑动窗口；OPEN：重试时间点；HALF_OPEN：探测名额 + 新窗口），状态切换时整体替换。
- 调用分两步：`tryAcquirePermission()`（调用前）→ `onSuccess()/onError()`（调用后）。这样的拆分让熔断器既能包装同步调用，也能包装异步调用和响应式流。
- 对应的 Resilience4j 源码：`CircuitBreakerStateMachine`（状态机）、`FixedSizeSlidingWindowMetrics`（窗口）。
- 完整的状态机说明见 [docs/01-state-machine.md](../../docs/01-state-machine.md)。

## 动手练习

1. 把 HALF_OPEN 的判断改成「收到第一个失败就重新打开」，运行 `CompareWithResilience4jTest`，看看哪一步开始不一致。
2. 给手写版加上「慢调用」统计：`onSuccess(long durationMs)` 中耗时超过阈值的调用计为慢调用。
3. 把 `synchronized` 改成 `AtomicReference<StateData>` + CAS，参考 Resilience4j 的 `stateReference.getAndUpdate(...)`。

## 思考题

<details>
<summary>1. 为什么 OPEN 状态下也可能收到 onSuccess/onError？</summary>

在熔断打开之前就已经放行的调用（尤其是慢调用），在打开之后才返回结果。这些结果属于「上一个阶段」，直接忽略即可。
Resilience4j 的 OpenState 同样忽略它们，只是还会更新一下指标。
</details>

<details>
<summary>2. CallNotPermittedException 为什么不需要堆栈？</summary>

OPEN 状态下每个请求都会抛出它，高并发时每秒可能有成千上万次；`fillInStackTrace` 需要遍历整个调用栈，开销不小。
而它的含义很明确（熔断打开），堆栈的排查价值有限。Resilience4j 中由 `writable-stack-trace-enabled` 控制，默认为 true，可以按需关闭。
</details>
