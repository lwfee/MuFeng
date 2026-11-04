package com.mufeng.trade.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.trade.infrastructure.persistence.po.ParentOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 父订单分表 Mapper 接口
 */
@Mapper
public interface ParentOrderMapper extends BaseMapper<ParentOrderPO> {

    /**
     * 1. 支付成功原子更新：仅当状态为 10(待支付) 时更新为 20(已支付)
     */
    @Update("UPDATE t_parent_order " +
            "SET order_status = 20, pay_channel = #{payChannel}, channel_trade_no = #{channelTradeNo}, " +
            "pay_time = NOW(), version = version + 1 " +
            "WHERE parent_order_no = #{parentOrderNo} AND order_status = 10")
    int updatePaidCas(@Param("parentOrderNo") Long parentOrderNo,
                      @Param("payChannel") Integer payChannel,
                      @Param("channelTradeNo") String channelTradeNo);

    /**
     * 2. 超时关单原子更新：仅当状态为 10(待支付) 时更新为 30(超时已关闭)
     */
    @Update("UPDATE t_parent_order " +
            "SET order_status = 30, version = version + 1 " +
            "WHERE parent_order_no = #{parentOrderNo} AND order_status = 10")
    int closeUnpaidParentOrderCas(@Param("parentOrderNo") Long parentOrderNo);

    /**
     * 3. 全部子单退完时标记父订单为 40(已全部取消)
     */
    @Update("UPDATE t_parent_order " +
            "SET order_status = 40, version = version + 1 " +
            "WHERE parent_order_no = #{parentOrderNo} AND order_status = 20")
    int markAllCancelledCas(@Param("parentOrderNo") Long parentOrderNo);
}