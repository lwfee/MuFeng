package com.mufeng.marketing.domain.repository;

import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import com.mufeng.marketing.domain.model.entity.UserCouponEntity;

import java.util.List;

/**
 * 营销优惠券领域仓储接口
 */
public interface CouponRepository {

    /**
     * 保存优惠券模板并预热至本地缓存
     */
    void saveTemplate(CouponTemplateEntity template);

    /**
     * 优先从 Caffeine 本地高速缓存获取券模板（支撑 8000 QPS 试算），未命中则回源 DB
     */
    CouponTemplateEntity getTemplateByIdWithCache(Long templateId);

    /**
     * 统计某用户已领取某模板券的数量
     */
    int countUserReceived(Long userId, Long templateId);

    /**
     * 原子扣减优惠券模板可领库存并插入用户领券记录
     */
    Long deductStockAndSaveUserCoupon(Long userId, CouponTemplateEntity template);

    /**
     * 查询用户单张优惠券实例
     */
    UserCouponEntity getUserCouponById(Long userCouponId);

    /**
     * 查询用户当前所有可用优惠券列表
     */
    List<UserCouponEntity> listAvailableUserCoupons(Long userId);

    /**
     * 提单锁券：基于 CAS 状态机将用户券从 UNUSED(1) 更新为 LOCKED(2)
     */
    boolean lockUserCoupon(Long userCouponId, Long userId, Long parentOrderNo);

    /**
     * 关单/全退解券：基于 CAS 状态机将用户券从 LOCKED(2) 恢复为 UNUSED(1)
     */
    boolean releaseUserCoupon(Long userId, Long parentOrderNo);
}