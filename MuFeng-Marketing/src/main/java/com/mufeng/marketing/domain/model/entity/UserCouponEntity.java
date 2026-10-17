package com.mufeng.marketing.domain.model.entity;

import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.marketing.domain.model.vo.UserCouponStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户优惠券实例领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCouponEntity {

    private Long id;
    private Long userId;
    private Long templateId;
    private Long lockedParentOrderNo;
    private Integer status;
    private LocalDateTime receiveTime;
    private LocalDateTime useTime;
    private LocalDateTime expireTime;
    private Integer version;

    /**
     * 校验该用户券是否可被当前买家使用
     */
    public void validateUsableBy(Long requestUserId) {
        if (this.userId == null || !this.userId.equals(requestUserId)) {
            throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE, "无法使用非本人持有的优惠券");
        }
        if (!UserCouponStatusEnum.UNUSED.getCode().equals(this.status)) {
            throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE, "优惠券已被锁定或已使用");
        }
        if (this.expireTime != null && LocalDateTime.now().isAfter(this.expireTime)) {
            throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE, "优惠券已过期");
        }
    }
}