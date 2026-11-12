# 02. 滑动窗口

> 相关模块：[L1-02](../level1-basic/02-resilience4j-config)、[L3-01](../level3-principle/01-mini-circuitbreaker)、[L3-02](../level3-principle/02-sliding-window)、[L4-01](../level4-production/01-tuning-simulator)
> 读完能回答：两种窗口怎样实现？各自在什么流量下会出问题？怎样选？

## 要解决的问题

失败率必须基于「最近」的调用来计算：一个月前的失败不应该影响现在的判断。「最近」可以按次数定义，也可以按时间定义。

## 核心实现

**COUNT_BASED：环形缓冲区**

```
size = 5        [F][S][S][F][S]
                          ^ head
记录新结果：如果已满，先从统计中减掉 head 处最老的结果 → 写入新结果 → head 后移
```

每次记录 O(1)。源码：`FixedSizeSlidingWindowMetrics`。手写版：[CountBasedSlidingWindow](../level3-principle/01-mini-circuitbreaker/src/main/java/com/kai/learn/cbdemo/l3/mini/CountBasedSlidingWindow.java)。

**TIME_BASED：按秒分桶**

```
window = 5s     秒:  100   101   102   103   104   | 105 到来时，复用下标 0 的桶，先清空
                桶: [3/1] [5/0] [2/2] [0/0] [4/1]
```

源码：`SlidingTimeWindowMetrics`，额外维护一个全局的 `TotalAggregation`，桶过期时把它的数据减掉，读取是 O(1)。
手写版：[MiniTimeBasedWindow](../level3-principle/02-sliding-window/src/main/java/com/kai/learn/cbdemo/l3/window/MiniTimeBasedWindow.java)。

## 两种窗口的特性

| | COUNT_BASED | TIME_BASED |
|---|---|---|
| 固定的是 | 样本数 | 时间跨度 |
| 随流量变化的是 | 覆盖的时间跨度 | 样本数 |
| 低流量时的问题 | 窗口跨越很长时间，**数据陈旧**（L3-02 实验一：下游恢复 5 分钟后仍显示 50%） | 样本太少，靠 minimumNumberOfCalls 兜底 |
| 高流量时的问题 | 只覆盖很短的时间，**对抖动过于敏感**（实验二：0.5 秒的抖动 → 100%） | 结果稳定 |
| 内存 | 与窗口大小成正比 | 与秒数成正比，与流量无关 |

## 设计取舍与选择建议

- 流量稳定、QPS 较高（> 10）的接口：TIME_BASED，窗口 5~10 秒，minimumNumberOfCalls 设为窗口内正常流量的 20%~50%。
- QPS 很低的接口：两种都不理想。宁可设置较大的 minimumNumberOfCalls（少熔断），依靠超时控制保护线程。
- 窗口越大越稳定，但发现故障越慢：TIME_BASED 窗口 W 秒、故障失败率 f、阈值 t，发现故障大约需要 `W × t / f` 秒（L4-01 的实测数据与此吻合）。

## 常见问题

<details>
<summary>failureRate 返回 -1 是什么意思？</summary>

窗口中的调用次数少于 minimumNumberOfCalls，样本不足，不做判断。不是错误。
</details>
