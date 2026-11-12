package com.kai.learn.cbdemo.downstream;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 下游业务接口。
 * <ul>
 *   <li>{@code /api/data}：行为由当前故障模式（{@link ChaosState}）决定，大多数示例都调用它</li>
 *   <li>{@code /api/slow?delayMs=}：固定延迟，用于超时实验，不受故障模式影响</li>
 *   <li>{@code /api/status/{code}}：返回指定的 HTTP 状态码，用于「哪些异常应该计入失败」的实验</li>
 * </ul>
 */
@RestController
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);

    private final ChaosState state;

    public DataController(ChaosState state) {
        this.state = state;
    }

    @GetMapping("/api/data")
    public ResponseEntity<ApiResult<Map<String, Object>>> data(@RequestParam(defaultValue = "anonymous") String caller) {
        long n = state.incrementReceived();
        ChaosState.Config config = state.get();
        log.info("#{} 收到 {} 的请求，当前模式 {}", n, caller, config.mode());

        return switch (config.mode()) {
            case NORMAL -> ok(n, caller);
            case SLOW -> {
                sleep(config.delayMs());
                yield ok(n, caller);
            }
            case ERROR -> error("模拟下游故障（ERROR 模式）");
            case FLAKY -> state.shouldFail(config.failRate()) ? error("模拟随机失败（FLAKY 模式）") : ok(n, caller);
        };
    }

    @GetMapping("/api/slow")
    public ApiResult<Map<String, Object>> slow(@RequestParam(defaultValue = "2000") long delayMs) {
        long n = state.incrementReceived();
        sleep(delayMs);
        return ApiResult.ok(Map.of("seq", n, "delayMs", delayMs));
    }

    @GetMapping("/api/status/{code}")
    public ResponseEntity<ApiResult<Void>> status(@PathVariable int code) {
        state.incrementReceived();
        return ResponseEntity.status(code).body(ApiResult.fallback("下游按要求返回 " + code));
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> ok(long n, String caller) {
        return ResponseEntity.ok(ApiResult.ok(Map.of("seq", n, "caller", caller, "item", "商品-" + n)));
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> error(String reason) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResult.fallback(reason));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // 调用方超时取消时可能会中断这个线程，恢复中断标记后直接返回
            Thread.currentThread().interrupt();
        }
    }
}
