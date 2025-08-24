package com.hmdp.mapper;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Select("SELECT * FROM tb_voucher_order WHERE status = #{status} AND create_time < #{time} ORDER BY create_time ASC LIMIT #{limit} OFFSET #{offset}")
    List<VoucherOrder> queryTimeoutVoucher(@Param("status") int status,
                                           @Param("time") LocalDateTime time,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Select("select * from tb_voucher_order where id > #{id} and create_time < #{time} and status = #{status} order by id ASC limit #{limit}")
    List<VoucherOrder> queryVoucherOrderAfterId(@Param("id") Long id, @Param("time") LocalDateTime time,
                                                @Param("limit") int limit, @Param("status") int status);


    @Update("update tb_voucher_order set status = #{newStatus} where id = #{id} and status = #{newStatus}")
    int updateOrderStatus(@Param("id") Long id,
                          @Param("oldStatus") int oldStatus,
                          @Param("newStatus") int newStatus);


}
