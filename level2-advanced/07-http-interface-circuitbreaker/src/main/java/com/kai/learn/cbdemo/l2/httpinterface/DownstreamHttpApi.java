package com.kai.learn.cbdemo.l2.httpinterface;

import java.util.Map;

import com.kai.learn.cbdemo.common.ApiResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * 声明式 HTTP 接口：只有接口，没有实现，Spring 在运行时生成代理。
 * 与 OpenFeign 的写法很像，但它是 Spring Framework 自带的功能，不需要额外的依赖。
 */
@HttpExchange("/api")
public interface DownstreamHttpApi {

    @GetExchange("/data")
    ApiResult<Map<String, Object>> data(@RequestParam("caller") String caller);

    @GetExchange("/slow")
    ApiResult<Map<String, Object>> slow(@RequestParam("delayMs") long delayMs);
}
