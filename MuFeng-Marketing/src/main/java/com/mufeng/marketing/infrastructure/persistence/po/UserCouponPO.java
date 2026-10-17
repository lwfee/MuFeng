package com.mufeng.marketing.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户优惠券实例表持久化对象 (对应 t_user_coupon)
 */
@Data
@TableName("t_user_coupon")
public class UserCouponPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long userId;
    private Long templateId;
    private Long lockedParentOrderNo;
    private Integer status;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime expireTime;
    private Integer version;
}