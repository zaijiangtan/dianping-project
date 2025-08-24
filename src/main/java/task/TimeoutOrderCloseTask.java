package task;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CLOSE_TIMEOUT_ORDER_KEY;

@Component
@Slf4j
public class TimeoutOrderCloseTask {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private KafkaTemplate<String, Long> kafkaTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> ROLLBACK_SECKILL_SCRIPT;
    static {
        ROLLBACK_SECKILL_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/rollback_seckill.lua"));
    }

    @Scheduled(cron = "0 * * * * *")
    public void closeOrder() {
        log.info("开始执行未支付订单关闭任务...");
        // 创建锁对象
        RLock redisLock = redissonClient.getLock(CLOSE_TIMEOUT_ORDER_KEY);
        // 尝试获取锁
        boolean isLock = redisLock.tryLock();
        // 判断
        if (!isLock) {
            // 获取锁失败
            log.info("已有任务在执行，跳过本次执行");
            return;
        }
        try {
            LocalDateTime time = LocalDateTime.now().minusMinutes(15);
            Long lastId = 0L;
            int limit = 500;

            while (true) {
                List<VoucherOrder> voucherOrders =
                        voucherOrderMapper.queryVoucherOrderAfterId(lastId, time, limit, 1);

                if (voucherOrders.isEmpty()) {
                    break;
                }

                for (VoucherOrder voucherOrder : voucherOrders) {
                    Long id = voucherOrder.getId();
                    int result = voucherOrderMapper.updateOrderStatus(id,
                            OrderStatus.UNPAID.getCode(), OrderStatus.CANCELED.getCode());
                    if (result == 1) {
                        // 获取用户id
                        Long userId = voucherOrder.getUserId();
                        // 获取优惠卷id
                        Long voucherId = voucherOrder.getVoucherId();
                        stringRedisTemplate.execute(
                                ROLLBACK_SECKILL_SCRIPT,
                                Collections.emptyList(),
                                voucherId.toString(), userId.toString()
                        );
                        kafkaTemplate.send("timeout-order", id);
                        log.info("订单 {} 超时关闭成功，已发送 Kafka 消息", id);
                    }
                }

                // 如果不足一页，说明查完了
                if (voucherOrders.size() < limit) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("订单关闭任务执行异常", e);
        } finally {
            // 释放锁
            redisLock.unlock();
        }
    }
}
