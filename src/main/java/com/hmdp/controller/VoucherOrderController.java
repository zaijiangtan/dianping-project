package com.hmdp.controller;


import com.hmdp.annotation.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

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

    // @RateLimiter(key = "seckill_limit:", window = 1, limit = 500)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @PostMapping("/payment/callback")
    public Result paymentCallback(@RequestBody Map<String, Object> data) {
        Long orderId = Long.valueOf(data.get("orderId").toString());
        String tradeStatus = data.get("status").toString(); // SUCCESS / FAILED

        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order != null && order.getStatus() == 0) { // 只更新未支付订单
            order.setStatus("SUCCESS".equals(tradeStatus) ? 1 : 2);
            voucherOrderService.updateById(order);

            // SSE 通知前端
            /*OrderSseController.sendOrderStatus(order.getUserId(), Map.of(
                    "orderId", orderId,
                    "status", order.getStatus() == 1 ? "PAID" : "PAY_FAILED"
            ));*/
        }
        return Result.ok(); // 返回给支付平台，表示接收成功
    }

}
