package com.mufeng.trade.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单项营销与出资方分摊快照持久化对象 (对应 t_order_item_promo_snapshot)
 */
@Data
@TableName("t_order_item_promo_snapshot")
public class OrderItemPromoSnapshotPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long parentOrderNo;
    private Long subOrderNo;
    private Long userId;
    private Long merchantId;
    private Long skuId;
    private Integer buyQuantity;
    private Long originTotalAmount;
    private Long totalDiscountAmount;
    private Long platformSubsidyAmount;
    private Long merchantDiscountAmount;
    private Long actualPayAmount;
    private Integer refundStatus;
    private LocalDateTime createdAt;
}