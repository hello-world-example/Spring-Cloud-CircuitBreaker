package com.kai.learn.cbdemo.downstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 模拟下游服务，端口 8081。
 *
 * <p>用法：
 * <pre>
 *   curl localhost:8081/chaos                               # 查看当前故障模式和收到的请求数
 *   curl -X POST 'localhost:8081/chaos?mode=ERROR'          # 之后 /api/data 全部返回 500
 *   curl -X POST 'localhost:8081/chaos?mode=SLOW&amp;delayMs=2000'
 *   curl -X POST 'localhost:8081/chaos?mode=FLAKY&amp;failRate=50'
 *   curl -X POST 'localhost:8081/chaos?mode=NORMAL'         # 恢复正常
 * </pre>
 *
 * <p>【动手】熔断器处于 OPEN 状态时，观察 {@code /chaos} 返回的 {@code receivedCount} 是否还在增长。
 * 不再增长，说明请求在调用方就被拦截了，下游得到了喘息的机会，这正是熔断要达到的效果。
 */
@SpringBootApplication
public class DownstreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(DownstreamApplication.class, args);
    }
}
