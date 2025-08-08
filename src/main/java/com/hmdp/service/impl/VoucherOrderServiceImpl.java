package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    //private final BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.order";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams streams.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断获取消息是否成功
                    if (list == null || list.isEmpty()) {
                        // 获取失败，说明当前没有消息，继续下一次循环
                        continue;
                    }
                    // 3.获取成功，进行下单
                    // 3.1.获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    // 3.2.向数据库增加修改下单信息
                    // 3.2.1.保存订单到数据库
                    save(voucherOrder);
                    // 3.2.2.减库存
                    boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                            .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                            .update();
                    if (!success) {
                        log.error("减库存失败");
                    }

                    // 3.3.向消息队列确认信息
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常{}", String.valueOf(e));
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams streams.order 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 2.判断获取消息是否成功
                    if (list == null || list.isEmpty()) {
                        // 获取失败，说明pending-list中没有消息，结束循环
                        break;
                    }

                    // 3.获取成功，进行下单
                    // 3.1.获取订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    // 3.2.向数据库增加修改下单信息
                    // 3.2.1.保存订单到数据库
                    save(voucherOrder);
                    // 3.2.2.减库存
                    boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                            .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                            .update();
                    if (!success) {
                        log.error("减库存失败");
                    }

                    // 3.3.向消息队列确认信息
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常{}", String.valueOf(e));
                }
            }
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.执行lua脚本，判断用户是否具备购买资格
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        // 2.返回不为0，即没有购买资格
        if (result != 0) {
            // 2.1 库存不足返回1, 重复下单返回2
            return Result.fail(result.intValue() == 1 ? "库存不足": "不能重复下单");
        }

        /*// 3.有购买资格，将下单信息保存到阻塞队列
        // 3.1.创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1.1代金券id
        voucherOrder.setVoucherId(voucherId);
        // 3.1.2
        voucherOrder.setId(orderId);
        // 3.1.3用户id
        voucherOrder.setUserId(userId);
        // 3.2.将订单加入阻塞队列
        orderQueue.add(voucherOrder);*/

        // 4.返回订单id
        return Result.ok(orderId);
    }


    /*private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderQueue.take();
                    // 2.保存订单到数据库
                    save(voucherOrder);
                    // 3.减库存
                    boolean success;
                    success = seckillVoucherService.update().setSql("stock = stock - 1")
                            .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                            .update();
                    if (!success) {
                        log.error("阻塞队列修改数据库-减库存失败");
                    }
                } catch (InterruptedException e) {
                    log.error("写入数据库出现异常{}", String.valueOf(e));
                }
            }
        }
    }*/

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.获取秒杀优惠劵订单信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2.判断是否在开始时间之前
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀还未开始！");
        }
        // 3.判断是否在结束时间之后
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        // 4.判断是否还有库存
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("优惠卷库存不足！");
        }

        // 5.判断该用户是否下过订单-实现一人一单
        // 5.1.获取用户id
        Long userId = UserHolder.getUser().getId();
        // 5.2.创建锁对象
        // SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 5.3.获取锁
        boolean success = lock.tryLock();
        // 5.4.判断是否获取成功
        if (!success) {
            // 5.5 失败返回错误信息
            return Result.fail("每个用户只能下一单");
        }

        // 5.6成功则创建订单
        try {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 用代理对象调用并返回创建订单的事务
            return proxy.createSeckillVoucher(voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    @Override
    public Result createSeckillVoucher(Long voucherId) {
        // 6.1.获取用户id
        Long userId = UserHolder.getUser().getId();
        // 6.2.判断数据库中用户是否下过订单
        int count = query().eq("user_id", userId).eq("voucher_Id", voucherId).count();
        if (count > 0) {
            // 6.3.用户已经下过一单
            return Result.fail("每个用户只能下一单");
        }

        // 7.减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 8.创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        // 8.1代金券id
        voucherOrder.setVoucherId(voucherId);
        // 8.2订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 8.3用户id
        voucherOrder.setUserId(userId);

        // 9.保存订单
        save(voucherOrder);

        // 10.返回订单id
        return Result.ok(orderId);
    }*/
}
