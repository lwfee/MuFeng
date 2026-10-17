package com.mufeng.marketing.domain.service;

import com.mufeng.api.marketing.dto.CartItemDTO;
import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.common.util.ApportionUtil;
import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import com.mufeng.marketing.domain.model.vo.CouponScopeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 营销算价与双层最大余数法分摊领域服务
 */
@Service
public class PricingDomainService {

    /**
     * 执行订单/购物车实时算价与双层精度分摊
     *
     * @param cartItems      待算价商品项列表
     * @param couponTemplate 使用的优惠券模板（若未用券则传 null）
     * @param userCouponId   用户优惠券实例ID（若未用券则传 null）
     * @return 精确到分的汇总结果与各商品营销出资快照
     */
    public PriceCalcRespDTO calculateAndApportion(List<CartItemDTO> cartItems,
                                                  CouponTemplateEntity couponTemplate,
                                                  Long userCouponId) {
        long totalOriginAmount = 0L;
        long eligibleOriginAmount = 0L;
        List<Long> apportionWeights = new ArrayList<>(cartItems.size());

        // 1. 遍历商品项，识别哪些商品符合优惠券适用范围，并构建分摊权重向量
        for (CartItemDTO item : cartItems) {
            long itemOrigin = item.calcOriginTotalAmount();
            totalOriginAmount += itemOrigin;

            if (couponTemplate != null && isItemEligibleForCoupon(item, couponTemplate)) {
                eligibleOriginAmount += itemOrigin;
                apportionWeights.add(itemOrigin);
            } else {
                // 不符合优惠券范围的商品，分摊权重设为 0（分摊 0 元优惠）
                apportionWeights.add(0L);
            }
        }

        // 2. 计算优惠券可产生的总优惠金额
        long totalDiscountAmount = 0L;
        if (couponTemplate != null) {
            couponTemplate.validateAvailable();
            totalDiscountAmount = couponTemplate.calcTotalDiscount(eligibleOriginAmount);
        }

        // 3. 第一层最大余数法分摊：将总优惠额按商品原价权重分摊至各个商品项
        List<Long> itemDiscounts = totalDiscountAmount > 0
                ? ApportionUtil.apportionByWeights(totalDiscountAmount, apportionWeights)
                : apportionsOfZero(cartItems.size());

        // 4. 第二层出资比例拆解：将每个商品的优惠额拆分为【平台补贴额】与【商家让利额】
        int subsidyRatio = couponTemplate != null ? couponTemplate.getPlatformSubsidyRatio() : 0;
        long totalPlatformSubsidy = 0L;
        long totalMerchantDiscount = 0L;
        List<ItemPromoSnapshotDTO> snapshots = new ArrayList<>(cartItems.size());

        for (int i = 0; i < cartItems.size(); i++) {
            CartItemDTO item = cartItems.get(i);
            long itemOrigin = item.calcOriginTotalAmount();
            long itemDiscount = itemDiscounts.get(i);

            ApportionUtil.SubsidySplit split = ApportionUtil.splitPlatformAndMerchant(itemDiscount, subsidyRatio);
            long platformSubsidy = split.platformSubsidyAmount();
            long merchantDiscount = split.merchantDiscountAmount();
            long actualPay = itemOrigin - itemDiscount;

            totalPlatformSubsidy += platformSubsidy;
            totalMerchantDiscount += merchantDiscount;

            snapshots.add(ItemPromoSnapshotDTO.builder()
                    .skuId(item.getSkuId())
                    .merchantId(item.getMerchantId())
                    .buyQuantity(item.getBuyQuantity())
                    .originTotalAmount(itemOrigin)
                    .totalDiscountAmount(itemDiscount)
                    .platformSubsidyAmount(platformSubsidy)
                    .merchantDiscountAmount(merchantDiscount)
                    .actualPayAmount(actualPay)
                    .build());
        }

        return PriceCalcRespDTO.builder()
                .totalOriginAmount(totalOriginAmount)
                .totalDiscountAmount(totalDiscountAmount)
                .totalPlatformSubsidyAmount(totalPlatformSubsidy)
                .totalMerchantDiscountAmount(totalMerchantDiscount)
                .totalPayAmount(totalOriginAmount - totalDiscountAmount)
                .usedUserCouponId(totalDiscountAmount > 0 ? userCouponId : null)
                .itemSnapshots(snapshots)
                .build();
    }

    /**
     * 判断商品是否满足优惠券的适用范围（全平台通用 vs 指定店铺专用）
     */
    private boolean isItemEligibleForCoupon(CartItemDTO item, CouponTemplateEntity template) {
        CouponScopeTypeEnum scope = CouponScopeTypeEnum.of(template.getScopeType());
        if (scope == CouponScopeTypeEnum.ALL_PLATFORM) {
            return true;
        }
        if (scope == CouponScopeTypeEnum.MERCHANT_ONLY) {
            return template.getMerchantId() != null && template.getMerchantId().equals(item.getMerchantId());
        }
        return false;
    }

    private List<Long> apportionsOfZero(int size) {
        List<Long> zeros = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zeros.add(0L);
        }
        return zeros;
    }
}