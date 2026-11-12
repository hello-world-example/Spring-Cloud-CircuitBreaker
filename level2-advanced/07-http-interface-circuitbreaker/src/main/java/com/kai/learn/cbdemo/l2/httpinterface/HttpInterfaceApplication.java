package com.kai.learn.cbdemo.l2.httpinterface;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.httpservice.HttpServiceFallback;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * L2-07：Spring HTTP Interface（{@code @HttpExchange}）+ CircuitBreaker。
 *
 * 【是什么】Spring Framework 7 / Spring Boot 4 的 <b>HTTP Service Client</b>：用 {@link ImportHttpServices}
 * 按「组」注册 HTTP 接口代理，同组的接口共用一个 RestClient 和一份配置（spring.http.serviceclient.&lt;group&gt;.*）。
 *
 * 【原理】Spring Cloud Commons 提供了 {@code CircuitBreakerRestClientHttpServiceGroupConfigurer}，
 * 它为每个组装配一个 {@code CircuitBreakerAdapterDecorator}，把这个组中所有接口方法的调用包进
 * {@code circuitBreakerFactory.create(组名).run(...)} 中。所以：
 * <ul>
 *   <li>熔断器 id = <b>组名</b>（本例为 downstream），同组的所有方法共用一个熔断器</li>
 *   <li>降级类通过 {@link HttpServiceFallback} 声明，按「方法名 + 参数类型」匹配降级方法</li>
 *   <li>开关：spring.cloud.circuitbreaker.http-services.enabled（默认 true）</li>
 * </ul>
 *
 * 【对比】OpenFeign（L2-03）是按「方法」划分熔断器，HTTP Interface 是按「组」划分；
 * OpenFeign 的降级是 Spring Bean，HTTP Interface 的降级类由框架通过反射直接实例化（不是 Bean，不能注入依赖）。
 *
 * <p>代码导读顺序：{@link DownstreamHttpApi} → {@link DownstreamHttpApiFallbacks} → application.yml
 * → {@link HttpInterfaceDemoController}
 */
@SpringBootApplication
@ImportHttpServices(group = "downstream", types = DownstreamHttpApi.class)
// 【坑】@HttpServiceFallback 必须标注在一个 Spring Bean 上（框架通过 getBeansWithAnnotation 查找），
// 标注在降级类自身上是不会生效的。这里标注在启动类（它本身也是一个 Bean）上
@HttpServiceFallback(value = DownstreamHttpApiFallbacks.class, group = "downstream")
public class HttpInterfaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpInterfaceApplication.class, args);
    }
}
