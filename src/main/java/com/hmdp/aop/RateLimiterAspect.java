package com.hmdp.aop;

import com.hmdp.exception.RateLimitException;
import com.hmdp.utils.UserHolder;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.hmdp.annotation.RateLimiter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
public class RateLimiterAspect {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> LIMITER_SCRIPT;

    static{
        LIMITER_SCRIPT = new DefaultRedisScript<>();
        LIMITER_SCRIPT.setLocation(new ClassPathResource("lua/limiter.lua"));
        LIMITER_SCRIPT.setResultType(Long.class);
    }

    @Before("@annotation(rateLimiter)")
    public void before(JoinPoint joinPoint, RateLimiter rateLimiter) {
        // 1.获取滑动窗口lua脚本需要的参数
        // 1.1 存入redis的key
        String key = buildKey(joinPoint, rateLimiter);
        // 1.2 时间窗口
        int window = rateLimiter.window();
        // 1.3 时间窗口内限制的请求次数
        int limit = rateLimiter.limit();
        // 1.4 当前时间
        long now = System.currentTimeMillis();

        // 2.执行lua脚本
        // execute(RedisScript<T> script, List<K> keys, Object... args);
        Long result = stringRedisTemplate.execute(
                LIMITER_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(window), String.valueOf(limit), String.valueOf(now)
        );
        // 3.获取执行结果，判断是否通行
        if (result.equals(0L)) {
            throw new RateLimitException(rateLimiter.message());
        }
    }

    /**
     * 构建key
     */
    private String buildKey(JoinPoint joinPoint, RateLimiter rateLimiter) {
        String keyPrefix = rateLimiter.key();
        StringBuilder keyBuilder = new StringBuilder(keyPrefix);

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 添加类名和方法名
        keyBuilder.append(method.getDeclaringClass().getName())
                .append(":")
                .append(method.getName());

        // 根据限流类型添加额外维度
        switch (rateLimiter.type()) {
            case IP:
                keyBuilder.append(":ip:").append(getClientIp());
                break;
            case USER:
                keyBuilder.append(":user:").append(getCurrentUserId());
                break;
            case METHOD:
            default:
                // 方法级限流使用默认key
                break;
        }

        return keyBuilder.toString();
    }

    /**
     * 获取当前用户id
     */
    private String getCurrentUserId() {
        try {
            return UserHolder.getUser().getId().toString();
        } catch (Exception e) {
            return "anonymous";
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

}
