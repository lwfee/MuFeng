package com.mufeng.marketing.domain.model.entity;

import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.common.util.ApportionUtil;
import com.mufeng.marketing.domain.model.vo.CouponTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 优惠券模板充血领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponTemplateEntity {

    private Long id;
    private String couponName;
    /**
     * 类型: 1-满减券, 2-折扣券, 3-直降津贴
     */
    private Integer couponType;
    /**
     * 适用范围: 1-全平台跨店, 2-指定店铺
     */
    private Integer scopeType;
    /**
     * 所属商家ID (0表示全平台券)
     */
    private Long merchantId;
    /**
     * 使用门槛金额(单位:分)
     */
    private Long thresholdAmount;
    /**
     * 立减面额(单位:分) 或 折扣万分比(如 8500 表示 85 折)
     */
    private Long discountAmount;
    /**
     * 平台出资万分比(如 6667 表示平台承担 66.67%, 商家承担 33.33%)
     */
    private Integer platformSubsidyRatio;
    /**
     * 叠加规则位图 (BitMask)
     */
    private Long stackRuleBitmap;
    private Integer totalStock;
    private Integer availableStock;
    private Integer limitPerUser;
    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;
    private Integer status;

    /**
     * 校验模板当前是否处于有效可领/可用状态
     */
    public void validateAvailable() {
        LocalDateTime now = LocalDateTime.now();
        if (this.status == null || this.status != 1
                || now.isBefore(this.validStartTime)
                || now.isAfter(this.validEndTime)) {
            throw new BizException(ErrorCodeEnum.COUPON_TEMPLATE_NOT_FOUND);
        }
    }

    /**
     * 基于位图 (BitMask) 校验两张优惠券是否允许叠加使用
     * 规则：第 couponType 位为 1 表示允许与该类型叠加，为 0 表示互斥
     */
    public boolean canStackWith(CouponTemplateEntity other) {
        if (other == null || this.stackRuleBitmap == null) {
            return false;
        }
        long mask = 1L << other.getCouponType();
        return (this.stackRuleBitmap & mask) != 0L;
    }

    /**
     * 根据参与凑单的合格商品原总价，计算本券可产生的总优惠金额（单位：分）
     *
     * @param eligibleOriginTotal 满足适用范围的商品原总价（分）
     * @return 实际立减总金额（分，绝不超过商品总价减1分，防止0元购资损）
     */
    public long calcTotalDiscount(long eligibleOriginTotal) {
        if (eligibleOriginTotal < this.thresholdAmount) {
            throw new BizException(ErrorCodeEnum.COUPON_THRESHOLD_NOT_MET,
                    String.format("未达到优惠券使用门槛，还差 %.2f 元", (this.thresholdAmount - eligibleOriginTotal) / 100.0));
        }

        CouponTypeEnum typeEnum = CouponTypeEnum.of(this.couponType);
        if (typeEnum == null) {
            throw new BizException(ErrorCodeEnum.APPORTION_AMOUNT_ERROR, "未知的优惠券类型");
        }

        long rawDiscount = switch (typeEnum) {
            case FULL_REDUCTION, DIRECT_SUBSIDY -> this.discountAmount;
            case DISCOUNT_RATE -> {
                // discountAmount 为折扣率万分比（如 8500 代表 8.5 折，优惠率即为 10000 - 8500 = 1500）
                long discountRatio = ApportionUtil.RATIO_BASE - this.discountAmount;
                yield Math.round((double) (eligibleOriginTotal * discountRatio) / ApportionUtil.RATIO_BASE);
            }
        };

        // 资损兜底防线：最高优惠金额绝不允许超过商品原总价（至少保留 1 分钱实付）
        long maxAllowedDiscount = Math.max(0L, eligibleOriginTotal - 1L);
        return Math.min(rawDiscount, maxAllowedDiscount);
    }
}