package com.mufeng.common.util;

import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 营销优惠与清结算金额分摊工具类（全部以“分”为单位进行整数运算，彻底杜绝浮点精度资损）
 */
public final class ApportionUtil {

    /**
     * 万分比基数 (10000 = 100.00%)
     */
    public static final int RATIO_BASE = 10000;

    private ApportionUtil() {
    }

    /**
     * 按权重比例分摊总金额（前 N-1 项向下取整，最后一个有效权重项执行尾差兜底）
     *
     * @param totalAmountToSplit 待分摊的总金额（单位：分，必须 >= 0）
     * @param weights            各项的权重列表（如各商品原总价列表，单位：分）
     * @return 与 weights 长度严格一致的分摊金额列表，且各项之和严格等于 totalAmountToSplit
     */
    public static List<Long> apportionByWeights(long totalAmountToSplit, List<Long> weights) {
        if (weights == null || weights.isEmpty()) {
            return Collections.emptyList();
        }
        if (totalAmountToSplit < 0) {
            throw new BizException(ErrorCodeEnum.APPORTION_AMOUNT_ERROR, "待分摊总金额不能为负数");
        }
        int size = weights.size();
        if (totalAmountToSplit == 0) {
            return new ArrayList<>(Collections.nCopies(size, 0L));
        }

        long totalWeight = 0L;
        int lastPositiveIndex = -1;
        for (int i = 0; i < size; i++) {
            long w = weights.get(i) == null ? 0L : weights.get(i);
            if (w < 0) {
                throw new BizException(ErrorCodeEnum.APPORTION_AMOUNT_ERROR, "分摊权重不能为负数");
            }
            if (w > 0) {
                totalWeight += w;
                lastPositiveIndex = i;
            }
        }

        if (totalWeight <= 0 || lastPositiveIndex == -1) {
            throw new BizException(ErrorCodeEnum.APPORTION_AMOUNT_ERROR, "分摊总权重必须大于0");
        }

        List<Long> result = new ArrayList<>(size);
        long allocatedSum = 0L;

        for (int i = 0; i < size; i++) {
            long weight = weights.get(i) == null ? 0L : weights.get(i);
            if (weight == 0L) {
                result.add(0L);
                continue;
            }
            if (i == lastPositiveIndex) {
                // 最后一个非零权重项：直接用总金额减去前面已分摊的金额（尾差兜底）
                long remainder = totalAmountToSplit - allocatedSum;
                result.add(Math.max(0L, remainder));
            } else {
                // 前面的项：按比例向下取整
                long share = (totalAmountToSplit * weight) / totalWeight;
                result.add(share);
                allocatedSum += share;
            }
        }
        return result;
    }

    /**
     * 将单笔优惠金额拆分为【平台补贴金额】与【商家让利金额】
     *
     * @param itemDiscountAmount   当前商品分摊到的总优惠金额（单位：分）
     * @param platformSubsidyRatio 平台出资万分比（0 ~ 10000，例如 6667 代表平台承担 66.67%）
     * @return 拆分结果值对象（platformSubsidy + merchantDiscount == itemDiscountAmount）
     */
    public static SubsidySplit splitPlatformAndMerchant(long itemDiscountAmount, int platformSubsidyRatio) {
        if (itemDiscountAmount <= 0) {
            return new SubsidySplit(0L, 0L);
        }
        int validRatio = Math.max(0, Math.min(RATIO_BASE, platformSubsidyRatio));
        // 平台补贴金额按万分比四舍五入，商家让利金额做减法兜底，确保总和分毫不差
        long platformSubsidy = Math.round((double) (itemDiscountAmount * validRatio) / RATIO_BASE);
        long merchantDiscount = itemDiscountAmount - platformSubsidy;
        return new SubsidySplit(platformSubsidy, merchantDiscount);
    }

    /**
     * 平台补贴与商家让利拆分结果 Record (JDK 21 特性)
     *
     * @param platformSubsidyAmount  平台出资补贴金额（分）
     * @param merchantDiscountAmount 商家自主让利金额（分）
     */
    public record SubsidySplit(long platformSubsidyAmount, long merchantDiscountAmount) {
    }
}