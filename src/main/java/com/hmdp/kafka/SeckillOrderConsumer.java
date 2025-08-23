package com.hmdp.kafka;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;

@Component
@Slf4j
public class SeckillOrderConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @KafkaListener(topics = "seckill-voucher", groupId = "voucher-group")
    public void consumeVoucherOrder(VoucherOrder voucherOrder, Acknowledgment ack) {
        try {
            // 处理订单
            voucherOrderService.createVoucherOrder(voucherOrder);

            // 通知前端
            //String msg = "订单 " + voucherOrder.getId() + " 已创建成功";
            //webSocketService.sendMessageToUser(voucherOrder.getUserId(), msg);

            // 确认 offset
            ack.acknowledge();
        } catch (Exception e) {
            // 不 ack -> Kafka 会重新投递
            log.error("订单处理失败: {}", e.getMessage());
        }
    }
}
