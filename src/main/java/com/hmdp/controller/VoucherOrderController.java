package com.hmdp.controller;


import com.hmdp.annotation.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RateLimiter(key = "seckill_limit:", window = 1, limit = 500)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @RateLimiter(window = 1, limit = 10)
    @GetMapping("test/limit")
    public Result testlimit(){
        Long limiter = stringRedisTemplate.opsForValue().increment("limiter");
        // System.out.println(System.currentTimeMillis() + "==>" + limiter);
        return Result.ok();
    }
}
