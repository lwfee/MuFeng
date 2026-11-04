package com.mufeng.trade.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.trade.infrastructure.persistence.po.OrderItemPromoSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单项营销出资分摊快照 Mapper 接口
 */
@Mapper
public interface OrderItemPromoSnapshotMapper extends BaseMapper<OrderItemPromoSnapshotPO> {

    /**
     * CAS 标记单个商品项为已退款(2)，防止同一商品重复发起退货退款
     */
    @Update("UPDATE t_order_item_promo_snapshot " +
            "SET refund_status = 2 " +
            "WHERE sub_order_no = #{subOrderNo} AND sku_id = #{skuId} AND refund_status = 0")
    int markItemRefundedCas(@Param("subOrderNo") Long subOrderNo, @Param("skuId") Long skuId);
}