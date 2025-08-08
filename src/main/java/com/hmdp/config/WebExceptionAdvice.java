package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error("服务器运行时异常{}", e.toString(), e);
        return Result.fail("服务器异常");
    }

    // 其他通用异常处理
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("服务器内部异常{}", e.toString(), e);
        return Result.fail("系统异常，请联系管理员");
    }

    // 限流异常处理
    @ExceptionHandler(RateLimitException.class)
    public Result handleRateLimitException(RateLimitException e) {
        return Result.fail(e.getMessage());
    }
}
