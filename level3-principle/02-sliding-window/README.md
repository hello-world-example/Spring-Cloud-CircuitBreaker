# L3-02 滑动窗口

## 学习目标

- 按时间统计的滑动窗口是怎样实现的？
- COUNT_BASED 和 TIME_BASED 两种窗口分别在什么场景下会出问题？
- 怎样为一个接口选择窗口类型和大小？

## 运行

```bash
./mvnw -pl level3-principle/02-sliding-window test
```

控制台输出（`CountVsTimeWindowExperimentTest`）：

```
实验一：每分钟 1 次调用；第 0~4 分钟下游故障，之后恢复
分钟  结果  COUNT(最近10次)  TIME(最近60秒)
   4  失败      100.0%          100.0%
   5  成功       83.3%            0.0%
   9  成功       50.0%            0.0%     ← 下游恢复 5 分钟了，计数窗口仍然显示 50%
  14  成功        0.0%            0.0%     ← 计数窗口直到第 14 分钟才「忘掉」故障

实验二：200 QPS，最后 0.5 秒（100 次调用）全部失败
COUNT(最近100次) 失败率 = 100.0%   ← 窗口只覆盖了 0.5 秒
TIME(最近10秒)   失败率 = 5.6%     ← 窗口覆盖 2000 次调用，抖动被稀释
```

`MiniTimeBasedWindowTest`：手写的时间窗口与 Resilience4j 的 TIME_BASED 窗口，在 3000 步随机输入下失败率完全一致。

## 代码导读

1. [MiniTimeBasedWindow](src/main/java/com/kai/learn/cbdemo/l3/window/MiniTimeBasedWindow.java)：按秒分桶
2. [MiniTimeBasedWindowTest](src/test/java/com/kai/learn/cbdemo/l3/window/MiniTimeBasedWindowTest.java)：与 Resilience4j 对照，以及 METRICS_ONLY 状态的用法
3. [CountVsTimeWindowExperimentTest](src/test/java/com/kai/learn/cbdemo/l3/window/CountVsTimeWindowExperimentTest.java)：**核心**，两个实验

## 原理要点

- COUNT_BASED 窗口覆盖的**时间跨度**随流量变化；TIME_BASED 窗口覆盖的**样本数**随流量变化。
- Resilience4j 的 `SlidingTimeWindowMetrics` 按秒分桶，并维护一个全局的 `TotalAggregation`，桶过期时把它的数据减掉，读取是 O(1)。
- `minimumNumberOfCalls` 在 TIME_BASED 窗口中尤其重要：低流量时用它保证样本量。
- 详见 [docs/02-sliding-window.md](../../docs/02-sliding-window.md)。

## 动手练习

1. 把实验二的 COUNT 窗口改为 1000，失败率会变成多少？代价是什么？
2. 给 `MiniTimeBasedWindow` 加上 `TotalAggregation` 优化，让 `snapshot()` 变成 O(1)。

## 思考题

<details>
<summary>1. 一个每分钟只有几次调用的接口，应该怎么配置熔断？</summary>

样本太少，任何基于比率的判断都不可靠。可以：用 TIME_BASED 窗口 + 较大的 `minimumNumberOfCalls`（宁可不熔断）；
或者干脆不用熔断，只用超时 + 重试；或者改用 Framework Retry 这类「连续失败 N 次」的模型（L2-06）。
</details>
