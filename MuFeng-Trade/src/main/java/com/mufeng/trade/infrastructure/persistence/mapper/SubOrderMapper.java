package com.mufeng.trade.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.trade.infrastructure.persistence.po.SubOrderPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 店铺子订单分表 Mapper 接口
 */
@Mapper
public interface SubOrderMapper extends BaseMapper<SubOrderPO> {

    /**
     * 1. 父单支付成功后，批量激活名下所有子订单状态：10(待支付) -> 20(待发货)
     */
    @Update("UPDATE t_sub_order " +
            "SET order_status = 20, version = version + 1 " +
            "WHERE parent_order_no = #{parentOrderNo} AND order_status = 10")
    int activateSubOrdersOnPaid(@Param("parentOrderNo") Long parentOrderNo);

    /**
     * 2. 父单超时未付关闭时，批量关闭名下所有子订单：10(待支付) -> 60(交易关闭)
     */
    @Update("UPDATE t_sub_order " +
            "SET order_status = 60, version = version + 1 " +
            "WHERE parent_order_no = #{parentOrderNo} AND order_status = 10")
    int closeSubOrdersOnTimeout(@Param("parentOrderNo") Long parentOrderNo);

    /**
     * 3. 商家发货：20(待发货) -> 30(已发货待收货)
     */
    @Update("UPDATE t_sub_order " +
            "SET order_status = 30, deliver_time = NOW(), version = version + 1 " +
            "WHERE sub_order_no = #{subOrderNo} AND order_status = 20")
    int deliverSubOrderCas(@Param("subOrderNo") Long subOrderNo);

    /**
     * 4. 买家确认收货：30(已发货) -> 40(已确认收货)，同时标记 settle_status = 1(已触发清结算)
     */
    @Update("UPDATE t_sub_order " +
            "SET order_status = 40, settle_status = 1, confirm_time = NOW(), version = version + 1 " +
            "WHERE sub_order_no = #{subOrderNo} AND order_status = 30")
    int confirmSubOrderCas(@Param("subOrderNo") Long subOrderNo);

    /**
     * 5. 单品逆向退款累加更新（防超退并发校验）
     */
    @Update("UPDATE t_sub_order " +
            "SET refunded_amount = refunded_amount + #{refundAmount}, order_status = #{targetStatus}, version = version + 1 " +
            "WHERE sub_order_no = #{subOrderNo} AND (refunded_amount + #{refundAmount}) <= sub_pay_amount")
    int updateSubOrderRefundCas(@Param("subOrderNo") Long subOrderNo,
                                @Param("refundAmount") long refundAmount,
                                @Param("targetStatus") Integer targetStatus);
}