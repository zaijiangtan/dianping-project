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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
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
    }
}
