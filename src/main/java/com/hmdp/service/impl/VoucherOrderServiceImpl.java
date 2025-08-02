package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
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
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列
    private final BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

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
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.执行lua脚本，判断用户是否具备购买资格
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        // 2.返回不为0，即没有购买资格
        if (result != 0) {
            // 2.1 库存不足返回1, 重复下单返回2
            return Result.fail(result.intValue() == 1 ? "库存不足": "不能重复下单");
        }

        // 3.有购买资格，将下单信息保存到阻塞队列
        // 3.1.创建订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        // 3.1.1代金券id
        voucherOrder.setVoucherId(voucherId);
        // 3.1.2订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 3.1.3用户id
        voucherOrder.setUserId(userId);
        // 3.2.将订单加入阻塞队列
        orderQueue.add(voucherOrder);

        // 4.返回订单id
        return Result.ok(orderId);
    }

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
