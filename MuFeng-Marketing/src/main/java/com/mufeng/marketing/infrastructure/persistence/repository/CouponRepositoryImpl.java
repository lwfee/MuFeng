package com.mufeng.marketing.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import com.mufeng.marketing.domain.model.entity.UserCouponEntity;
import com.mufeng.marketing.domain.model.vo.UserCouponStatusEnum;
import com.mufeng.marketing.domain.repository.CouponRepository;
import com.mufeng.marketing.infrastructure.persistence.mapper.CouponTemplateMapper;
import com.mufeng.marketing.infrastructure.persistence.mapper.UserCouponMapper;
import com.mufeng.marketing.infrastructure.persistence.po.CouponTemplatePO;
import com.mufeng.marketing.infrastructure.persistence.po.UserCouponPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 营销优惠券仓储实现类（整合 Caffeine 本地高速缓存与 MySQL 持久化）
 */
@Repository
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final Cache<Long, CouponTemplateEntity> couponTemplateLocalCache;
    private final GeneSnowflakeIdGenerator idGenerator;

    @Override
    public void saveTemplate(CouponTemplateEntity entity) {
        if (entity.getId() == null) {
            entity.setId(idGenerator.nextId());
        }
        CouponTemplatePO po = toTemplatePO(entity);
        templateMapper.insert(po);
        // 写入成功后立即预热至 Caffeine 本地缓存
        couponTemplateLocalCache.put(entity.getId(), entity);
    }

    @Override
    public CouponTemplateEntity getTemplateByIdWithCache(Long templateId) {
        return couponTemplateLocalCache.get(templateId, id -> {
            CouponTemplatePO po = templateMapper.selectById(id);
            return po == null ? null : toTemplateEntity(po);
        });
    }

    @Override
    public int countUserReceived(Long userId, Long templateId) {
        Long count = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getTemplateId, templateId));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Long deductStockAndSaveUserCoupon(Long userId, CouponTemplateEntity template) {
        int rows = templateMapper.deductAvailableStock(template.getId());
        if (rows <= 0) {
            throw new BizException(ErrorCodeEnum.COUPON_STOCK_EXHAUSTED);
        }

        UserCouponPO userCouponPO = new UserCouponPO();
        userCouponPO.setId(idGenerator.nextId());
        userCouponPO.setUserId(userId);
        userCouponPO.setTemplateId(template.getId());
        userCouponPO.setStatus(UserCouponStatusEnum.UNUSED.getCode());
        userCouponPO.setReceiveTime(LocalDateTime.now());
        userCouponPO.setExpireTime(template.getValidEndTime());
        userCouponPO.setVersion(0);
        userCouponMapper.insert(userCouponPO);
        return userCouponPO.getId();
    }

    @Override
    public UserCouponEntity getUserCouponById(Long userCouponId) {
        UserCouponPO po = userCouponMapper.selectById(userCouponId);
        return po == null ? null : toUserCouponEntity(po);
    }

    @Override
    public List<UserCouponEntity> listAvailableUserCoupons(Long userId) {
        List<UserCouponPO> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCouponPO>()
                .eq(UserCouponPO::getUserId, userId)
                .eq(UserCouponPO::getStatus, UserCouponStatusEnum.UNUSED.getCode())
                .gt(UserCouponPO::getExpireTime, LocalDateTime.now()));
        return list.stream().map(this::toUserCouponEntity).collect(Collectors.toList());
    }

    @Override
    public boolean lockUserCoupon(Long userCouponId, Long userId, Long parentOrderNo) {
        return userCouponMapper.lockCouponCas(userCouponId, userId, parentOrderNo) > 0;
    }

    @Override
    public boolean releaseUserCoupon(Long userId, Long parentOrderNo) {
        return userCouponMapper.releaseCouponCas(userId, parentOrderNo) > 0;
    }

    private CouponTemplatePO toTemplatePO(CouponTemplateEntity entity) {
        CouponTemplatePO po = new CouponTemplatePO();
        po.setId(entity.getId());
        po.setCouponName(entity.getCouponName());
        po.setCouponType(entity.getCouponType());
        po.setScopeType(entity.getScopeType());
        po.setMerchantId(entity.getMerchantId());
        po.setThresholdAmount(entity.getThresholdAmount());
        po.setDiscountAmount(entity.getDiscountAmount());
        po.setPlatformSubsidyRatio(entity.getPlatformSubsidyRatio());
        po.setStackRuleBitmap(entity.getStackRuleBitmap());
        po.setTotalStock(entity.getTotalStock());
        po.setAvailableStock(entity.getAvailableStock());
        po.setLimitPerUser(entity.getLimitPerUser());
        po.setValidStartTime(entity.getValidStartTime());
        po.setValidEndTime(entity.getValidEndTime());
        po.setStatus(entity.getStatus());
        return po;
    }

    private CouponTemplateEntity toTemplateEntity(CouponTemplatePO po) {
        return CouponTemplateEntity.builder()
                .id(po.getId())
                .couponName(po.getCouponName())
                .couponType(po.getCouponType())
                .scopeType(po.getScopeType())
                .merchantId(po.getMerchantId())
                .thresholdAmount(po.getThresholdAmount())
                .discountAmount(po.getDiscountAmount())
                .platformSubsidyRatio(po.getPlatformSubsidyRatio())
                .stackRuleBitmap(po.getStackRuleBitmap())
                .totalStock(po.getTotalStock())
                .availableStock(po.getAvailableStock())
                .limitPerUser(po.getLimitPerUser())
                .validStartTime(po.getValidStartTime())
                .validEndTime(po.getValidEndTime())
                .status(po.getStatus())
                .build();
    }

    private UserCouponEntity toUserCouponEntity(UserCouponPO po) {
        return UserCouponEntity.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .templateId(po.getTemplateId())
                .lockedParentOrderNo(po.getLockedParentOrderNo())
                .status(po.getStatus())
                .receiveTime(po.getReceiveTime())
                .useTime(po.getUseTime())
                .expireTime(po.getExpireTime())
                .version(po.getVersion())
                .build();
    }
}