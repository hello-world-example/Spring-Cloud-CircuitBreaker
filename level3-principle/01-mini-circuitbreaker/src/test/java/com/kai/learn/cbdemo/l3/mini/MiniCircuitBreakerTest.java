package com.kai.learn.cbdemo.l3.mini;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.kai.learn.cbdemo.common.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.kai.learn.cbdemo.l3.mini.MiniCircuitBreaker.State.CLOSED;
import static com.kai.learn.cbdemo.l3.mini.MiniCircuitBreaker.State.HALF_OPEN;
import static com.kai.learn.cbdemo.l3.mini.MiniCircuitBreaker.State.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 每个测试对应状态机中的一条规则。测试方法名就是规则本身。
 */
class MiniCircuitBreakerTest {

    private MutableClock clock;
    private MiniCircuitBreaker cb;
    private final List<String> transitions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        // 窗口 10，至少 5 次，失败率 50%，OPEN 5 秒，探测 3 次
        cb = new MiniCircuitBreaker("test", new MiniCircuitBreakerConfig(10, 5, 50, Duration.ofSeconds(5), 3), clock);
        cb.onStateTransition(transitions::add);
    }

    @Test
    void 样本不足时即使全部失败也不打开() {
        fail(4);
        assertThat(cb.getState()).isEqualTo(CLOSED);
        assertThat(cb.getFailureRate()).isEqualTo(-1);
    }

    @Test
    void 失败率达到阈值时打开() {
        succeed(3);
        fail(3); // 6 次中失败 3 次 = 50%
        assertThat(cb.getState()).isEqualTo(OPEN);
        assertThat(transitions).containsExactly("CLOSED -> OPEN");
    }

    @Test
    void 打开后直接拒绝且不执行业务代码() {
        fail(5);
        int[] executed = {0};
        assertThatThrownBy(() -> cb.executeSupplier(() -> executed[0]++))
                .isInstanceOf(MiniCallNotPermittedException.class);
        assertThat(executed[0]).isZero();
        assertThat(cb.getNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void 等待时间到了之后由下一次调用触发半开() {
        fail(5);
        clock.advance(Duration.ofSeconds(5));
        // 只拨动时钟、没有调用时，状态仍然是 OPEN
        assertThat(cb.getState()).isEqualTo(OPEN);
        assertThat(cb.tryAcquirePermission()).isTrue();
        assertThat(cb.getState()).isEqualTo(HALF_OPEN);
    }

    @Test
    void 半开时探测成功则关闭() {
        fail(5);
        clock.advance(Duration.ofSeconds(5));
        succeed(3);
        assertThat(cb.getState()).isEqualTo(CLOSED);
        assertThat(transitions).containsExactly("CLOSED -> OPEN", "OPEN -> HALF_OPEN", "HALF_OPEN -> CLOSED");
    }

    @Test
    void 半开时探测失败率达到阈值则重新打开() {
        fail(5);
        clock.advance(Duration.ofSeconds(5));
        succeed(1);
        fail(2); // 3 次探测中失败 2 次 = 66%
        assertThat(cb.getState()).isEqualTo(OPEN);
    }

    @Test
    void 半开时探测名额用完后其余请求被拒绝() {
        fail(5);
        clock.advance(Duration.ofSeconds(5));
        assertThat(cb.tryAcquirePermission()).isTrue();
        assertThat(cb.tryAcquirePermission()).isTrue();
        assertThat(cb.tryAcquirePermission()).isTrue();
        // 3 个探测请求还没有返回结果，第 4 个请求被拒绝
        assertThat(cb.tryAcquirePermission()).isFalse();
    }

    @Test
    void 窗口满了之后最老的结果被挤出() {
        // 注意顺序：如果先失败 4 次，第 5 次调用时失败率就是 80%，熔断器早已打开
        succeed(6);
        fail(4);     // 窗口：[S S S S S S F F F F] = 40%，不打开
        assertThat(cb.getFailureRate()).isEqualTo(40);
        succeed(6);  // 挤出最老的 6 次成功：[F F F F S S S S S S]，仍然是 40%
        assertThat(cb.getFailureRate()).isEqualTo(40);
        succeed(4);  // 再挤出 4 次失败：全部是成功，0%
        assertThat(cb.getFailureRate()).isEqualTo(0);
        assertThat(cb.getState()).isEqualTo(CLOSED);
    }

    private void succeed(int n) {
        for (int i = 0; i < n; i++) {
            cb.executeSupplier(() -> "ok");
        }
    }

    private void fail(int n) {
        for (int i = 0; i < n; i++) {
            try {
                cb.executeSupplier(() -> {
                    throw new IllegalStateException("boom");
                });
            } catch (IllegalStateException | MiniCallNotPermittedException ignored) {
                // 预期中的异常
            }
        }
    }
}
