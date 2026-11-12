package com.kai.learn.cbdemo.l4.layered;

import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * 【是什么】商品详情页：由两个依赖组成。
 * <ul>
 *   <li><b>库存（强依赖）</b>：没有库存信息就不能下单，页面必须报错。保护策略：熔断 + 快速失败</li>
 *   <li><b>推荐（弱依赖）</b>：只是锦上添花，拿不到就不显示。保护策略：信号量舱壁（最多 5 个并发）
 *       + 300ms 超时 + 降级为空列表</li>
 * </ul>
 *
 * 【为什么】区分强弱依赖是容错设计的第一步：
 * <pre>
 *   依赖类型    失败时页面怎么办      保护目标                      手段
 *   强依赖      报错（或展示缓存）    快速失败，不要让用户久等      熔断 + 超时
 *   弱依赖      隐藏这一块            绝不能拖慢、拖垮主流程        舱壁 + 短超时 + 降级
 * </pre>
 *
 * 【原理】没有保护时，一个页面的耗时 = 库存耗时 + 推荐耗时。推荐变慢到 2 秒，每个页面请求就会占用容器线程 2 秒；
 * 容器线程池（本例中模拟为 20 个线程）很快被占满，后面的请求只能排队，<b>连只需要库存的请求也一起变慢</b>。
 * 这就是「弱依赖拖垮强依赖」，也是雪崩的起点。
 */
@Service
@RequiredArgsConstructor
public class ProductPageService {

    private final CircuitBreakerFactory<?, ?> factory;
    private final DownstreamApi api;

    public record Page(boolean inventoryOk, String inventoryMessage, List<String> recommendations,
                       boolean recommendDegraded) {
    }

    /** 有保护 */
    public Page buildProtected(long recommendDelayMs) {
        // 强依赖：失败时不降级成假数据，而是如实报告失败
        ApiResult<Map<String, Object>> inventory = factory.create("inventory").run(
                () -> api.data("l4-03"),
                t -> ApiResult.fallback("库存服务不可用：" + Errors.describe(t)));

        // 弱依赖：舱壁 + 短超时，任何失败都降级为「不展示推荐」
        ApiResult<Map<String, Object>> recommend = factory.create("recommend").run(
                () -> api.slow(recommendDelayMs),
                t -> ApiResult.fallback(Errors.describe(t)));

        boolean degraded = !ApiResult.SOURCE_DOWNSTREAM.equals(recommend.source());
        return new Page(inventory.success(), inventory.message(),
                degraded ? List.of() : List.of("推荐商品 A", "推荐商品 B"), degraded);
    }

    /** 无保护：直接调用 */
    public Page buildUnprotected(long recommendDelayMs) {
        ApiResult<Map<String, Object>> inventory = api.data("l4-03");
        api.slow(recommendDelayMs);
        return new Page(inventory.success(), inventory.message(), List.of("推荐商品 A", "推荐商品 B"), false);
    }
}
