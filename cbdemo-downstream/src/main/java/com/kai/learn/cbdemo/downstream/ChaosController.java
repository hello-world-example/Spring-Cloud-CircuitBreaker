package com.kai.learn.cbdemo.downstream;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行时切换故障模式，并查看下游实际收到了多少请求。
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class ChaosController {

    private final ChaosState state;

    @GetMapping("/chaos")
    public Map<String, Object> current() {
        return Map.of("config", state.get(), "receivedCount", state.receivedCount());
    }

    @PostMapping("/chaos")
    public Map<String, Object> change(@RequestParam ChaosState.Mode mode,
                                      @RequestParam(defaultValue = "2000") long delayMs,
                                      @RequestParam(defaultValue = "50") int failRate,
                                      @RequestParam(defaultValue = "true") boolean resetCount) {
        state.set(new ChaosState.Config(mode, delayMs, failRate));
        if (resetCount) {
            state.resetCount();
        }
        log.warn("故障模式切换为 {}", state.get());
        return current();
    }
}
