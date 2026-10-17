package com.mufeng.marketing.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券模板表持久化对象 (对应 t_coupon_template)
 */
@Data
@TableName("t_coupon_template")
public class CouponTemplatePO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String couponName;
    private Integer couponType;
    private Integer scopeType;
    private Long merchantId;
    private Long thresholdAmount;
    private Long discountAmount;
    private Integer platformSubsidyRatio;
    private Long stackRuleBitmap;
    private Integer totalStock;
    private Integer availableStock;
    private Integer limitPerUser;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}