package com.kai.learn.cbdemo.l1.timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import com.kai.learn.cbdemo.common.CallRecord;
import com.kai.learn.cbdemo.common.DownstreamApi;
import com.kai.learn.cbdemo.common.Errors;
import com.kai.learn.cbdemo.common.Timer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 三个实验：
 * <ol>
 *   <li>{@code /demo/timeout?delayMs=1000}：TimeLimiter 超时（fast-timeout 的超时时间为 500ms）</li>
 *   <li>{@code /demo/status/{code}?n=8}：4xx 不计入失败率，5xx 计入</li>
 *   <li>{@code /demo/no-fallback}：不提供降级函数时会怎样</li>
 * </ol>
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class TimeoutDemoController {

    private final CircuitBreakerFactory<?, ?> factory;
    private final CircuitBreakerRegistry registry;
    private final DownstreamApi api;
    private final FallbackResolver fallbackResolver;

    @GetMapping("/demo/timeout")
    public Map<String, Object> timeout(@RequestParam(defaultValue = "1000") long delayMs) {
        String callerThread = Thread.currentThread().getName();
        Timer.Timed<ApiResult<Map<String, Object>>> timed = Timer.time(() -> factory.create("fast-timeout").run(
                () -> {
                    // 【原理】这段代码并不在 Tomcat 线程中执行，而是被提交到 Factory 内部的线程池中执行，
                    // 这样 TimeLimiter 才能通过 Future.get(timeout) 实现「等待超时」。线程名可以证明这一点
                    String workerThread = Thread.currentThread().getName();
                    long start = System.currentTimeMillis();
                    try {
                        ApiResult<Map<String, Object>> r = api.slow(delayMs);
                        // 【坑】超时后 TimeLimiter 会调用 cancel(true) 中断这个线程。能不能真正停下来，取决于 HTTP 客户端是否响应中断：
                        //   - JDK HttpClient（本模块使用的）：会响应中断，抛出 "Request was interrupted"，后台线程大约在 500ms 时释放
                        //   - HttpURLConnection（SimpleClientHttpRequestFactory）：不响应中断，后台线程会一直等到下游返回或读超时，
                        //     然后打印下面这行日志
                        // 无论哪种情况，下游都<b>已经收到请求并会继续处理</b>（看 /chaos 中的 receivedCount）。
                        // 【动手】在 TimeoutApplication 中改用 new SimpleClientHttpRequestFactory()，再访问一次，对比日志
                        log.warn("[{}] 下游在 {}ms 后返回了结果，但调用方已经超时降级，这个结果被丢弃",
                                workerThread, System.currentTimeMillis() - start);
                        return r;
                    } catch (RuntimeException e) {
                        log.warn("[{}] 后台调用在 {}ms 后结束：{}", workerThread,
                                System.currentTimeMillis() - start, Errors.describe(e));
                        throw e;
                    }
                },
                fallbackResolver::resolve));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result", timed.value());
        result.put("costMs", timed.costMs());
        result.put("callerThread", callerThread);
        return result;
    }

    @GetMapping("/demo/status/{code}")
    public List<CallRecord> status(@PathVariable int code, @RequestParam(defaultValue = "8") int n) {
        List<CallRecord> records = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ApiResult<Map<String, Object>> r;
            try {
                r = factory.create("status").run(() -> api.status(code), fallbackResolver::resolve);
            } catch (HttpClientErrorException e) {
                // 4xx 被降级函数原样抛出
                r = new ApiResult<>(false, "client-error", "HTTP " + e.getStatusCode().value() + "（未降级，原样抛出）", null, null);
            }
            records.add(new CallRecord(i, r.source(), r.message(),
                    registry.circuitBreaker("status").getState().name(), 0, ""));
        }
        return records;
    }

    @GetMapping("/demo/no-fallback")
    public ApiResult<Map<String, Object>> noFallback() {
        // 【坑】不传降级函数时，任何异常都会被包装成 NoFallbackAvailableException 抛出。
        // 原始异常要从 getCause() 中获取，按异常类型统一处理时容易漏掉
        return factory.create("no-fallback").run(() -> api.status(503));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ApiResult<Void>> onClientError(HttpClientErrorException e) {
        return ResponseEntity.status(e.getStatusCode()).body(ApiResult.fallback("下游 4xx：" + e.getStatusCode()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> onError(RuntimeException e) {
        return ResponseEntity.internalServerError().body(Map.of(
                "exception", e.getClass().getName(),
                "cause", e.getCause() == null ? "-" : Errors.describe(e.getCause())));
    }
}
