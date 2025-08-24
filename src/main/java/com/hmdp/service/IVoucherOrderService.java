package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 *  服务类
 * </p>
 *
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠卷下单
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    void rollbackVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 更新已支付订单的状态
     * @param order
     */
    boolean updateStatus(VoucherOrder order);

    // Result createSeckillVoucher(Long voucherId);
}
