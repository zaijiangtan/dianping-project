package com.hmdp.controller;


import com.hmdp.annotation.RateLimiter;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.OrderStatus;
import com.hmdp.utils.OrderWebSocketHandler;
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


    /**
     * 秒杀接口
     * @param voucherId
     * @return
     */
    // @RateLimiter(key = "seckill_limit:", window = 1, limit = 500)
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 支付回调接口
     * @param data
     * @return
     */
    @PostMapping("/payment/callback")
    public Result paymentCallback(@RequestBody Map<String, Object> data) {
        // 1.获取订单id和支付结果
        Long orderId = Long.valueOf(data.get("orderId").toString());
        String tradeStatus = data.get("status").toString(); // SUCCESS / FAILED

        // 2.根据订单id获取订单信息
        VoucherOrder order = voucherOrderService.getById(orderId);

        // 3.判断支付结果
        if (tradeStatus.equals("FAILED")) {
            // 3.1支付失败，用WebSocket通知前端
            OrderWebSocketHandler.sendOrderMessage(orderId, "用户支付失败");
            return Result.ok();
        }

        // 3.2支付成功，尝试更新订单状态
        if (order != null && order.getStatus() == OrderStatus.UNPAID.getCode()) { // 只更新未支付订单-第一次检查
            // 4.更新订单状态，并获取更新结果
            boolean result = voucherOrderService.updateStatus(order);

            if (!result) {
                // 4.1更新订单失败
                // 通知前端订单已经超时
                OrderWebSocketHandler.sendOrderMessage(orderId, "订单超时");
                // 通知支付端更新失败，给用户退款
                return Result.fail("订单已经超时，支付失败");
            }

            // 4.2更新订单成功
            OrderWebSocketHandler.sendOrderMessage(orderId, "订单支付完成");
        }
        // 返回给支付平台，表示接收成功
        return Result.ok();
    }
}
