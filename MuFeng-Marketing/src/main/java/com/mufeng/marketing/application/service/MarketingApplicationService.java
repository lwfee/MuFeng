package com.mufeng.marketing.application.service;

import com.mufeng.api.marketing.dto.PriceCalcReqDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.common.lock.DistributedLock;
import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import com.mufeng.marketing.domain.model.entity.UserCouponEntity;
import com.mufeng.marketing.domain.repository.CouponRepository;
import com.mufeng.marketing.domain.service.PricingDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 营销算价应用编排服务（负责用例编排、分布式锁防重与本地事务边界控制）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingApplicationService {

    private final CouponRepository couponRepository;
    private final PricingDomainService pricingDomainService;

    /**
     * 1. 运营后台创建优惠券模板并预热至 Caffeine 缓存
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createCouponTemplate(CouponTemplateEntity template) {
        template.setAvailableStock(template.getTotalStock());
        template.setStatus(1);
        couponRepository.saveTemplate(template);
        log.info("[营销中心] 优惠券模板创建并预热成功, templateId: {}, name: {}", template.getId(), template.getCouponName());
        return template.getId();
    }

    /**
     * 2. 用户领取优惠券（基于用户+模板维度加分布式锁，防止并发刷券突破限领上限）
     */
    @DistributedLock(
            prefix = "lock:marketing:coupon:receive:",
            key = "#userId + ':' + #templateId",
            failMessage = "领券请求处理中，请勿频繁点击"
    )
    @Transactional(rollbackFor = Exception.class)
    public Long receiveCoupon(Long userId, Long templateId) {
        CouponTemplateEntity template = couponRepository.getTemplateByIdWithCache(templateId);
        if (template == null) {
            throw new BizException(ErrorCodeEnum.COUPON_TEMPLATE_NOT_FOUND);
        }
        template.validateAvailable();

        // 校验每人限领张数
        int receivedCount = couponRepository.countUserReceived(userId, templateId);
        if (receivedCount >= template.getLimitPerUser()) {
            throw new BizException(ErrorCodeEnum.COUPON_LIMIT_EXCEEDED);
        }

        // 原子扣减模板库存并落库用户券实例
        Long userCouponId = couponRepository.deductStockAndSaveUserCoupon(userId, template);
        log.info("[营销中心] 用户领券成功, userId: {}, templateId: {}, userCouponId: {}", userId, templateId, userCouponId);
        return userCouponId;
    }

    /**
     * 3. 购物车/收银台高频实时试算（纯内存计算，支持指定券试算或自动匹配最优券）
     */
    public PriceCalcRespDTO previewPrice(PriceCalcReqDTO req) {
        // 情况 A：用户明确指定了要使用的优惠券
        if (req.getUserCouponId() != null) {
            UserCouponEntity userCoupon = couponRepository.getUserCouponById(req.getUserCouponId());
            if (userCoupon == null) {
                throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE);
            }
            userCoupon.validateUsableBy(req.getUserId());
            CouponTemplateEntity template = couponRepository.getTemplateByIdWithCache(userCoupon.getTemplateId());
            return pricingDomainService.calculateAndApportion(req.getItems(), template, userCoupon.getId());
        }

        // 情况 B：用户未指定券，自动从其可用券包中试算挑选出优惠金额最大的一张（最优用券推荐）
        List<UserCouponEntity> availableCoupons = couponRepository.listAvailableUserCoupons(req.getUserId());
        PriceCalcRespDTO bestResult = pricingDomainService.calculateAndApportion(req.getItems(), null, null);

        for (UserCouponEntity candidate : availableCoupons) {
            try {
                CouponTemplateEntity template = couponRepository.getTemplateByIdWithCache(candidate.getTemplateId());
                if (template != null) {
                    PriceCalcRespDTO currentResult = pricingDomainService.calculateAndApportion(
                            req.getItems(), template, candidate.getId());
                    if (currentResult.getTotalDiscountAmount() > bestResult.getTotalDiscountAmount()) {
                        bestResult = currentResult;
                    }
                }
            } catch (BizException ignored) {
                // 候选券未达门槛或已失效时自动跳过，继续试算下一张
            }
        }
        return bestResult;
    }

    /**
     * 4. 正式提单：CAS 锁定用户优惠券并生成精确到分的双层分摊快照
     */
    @Transactional(rollbackFor = Exception.class)
    public PriceCalcRespDTO lockCouponAndCalc(PriceCalcReqDTO req) {
        if (req.getParentOrderNo() == null) {
            throw new BizException(ErrorCodeEnum.PARAM_VALID_ERROR, "提单锁券时父订单号不能为空");
        }

        // 若未使用优惠券，直接按原价返回零优惠分摊快照
        if (req.getUserCouponId() == null) {
            return pricingDomainService.calculateAndApportion(req.getItems(), null, null);
        }

        UserCouponEntity userCoupon = couponRepository.getUserCouponById(req.getUserCouponId());
        if (userCoupon == null) {
            throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE);
        }
        userCoupon.validateUsableBy(req.getUserId());

        CouponTemplateEntity template = couponRepository.getTemplateByIdWithCache(userCoupon.getTemplateId());
        if (template == null) {
            throw new BizException(ErrorCodeEnum.COUPON_TEMPLATE_NOT_FOUND);
        }

        // 先执行算价校验（若未达门槛会直接抛异常拦截，避免无效锁券）
        PriceCalcRespDTO calcResp = pricingDomainService.calculateAndApportion(
                req.getItems(), template, userCoupon.getId());

        // 数据库 CAS 原子锁定优惠券（防并发一券多单）
        boolean locked = couponRepository.lockUserCoupon(
                userCoupon.getId(), req.getUserId(), req.getParentOrderNo());
        if (!locked) {
            throw new BizException(ErrorCodeEnum.USER_COUPON_NOT_AVAILABLE, "优惠券已被其他订单锁定，请刷新重试");
        }

        log.info("[营销中心] 提单锁券与分摊计算成功, userId: {}, parentOrderNo: {}, userCouponId: {}, totalDiscount: {}分",
                req.getUserId(), req.getParentOrderNo(), userCoupon.getId(), calcResp.getTotalDiscountAmount());
        return calcResp;
    }

    /**
     * 5. 订单超时关闭或整单取消：CAS 幂等解锁归还优惠券
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseCoupon(Long userId, Long parentOrderNo) {
        boolean released = couponRepository.releaseUserCoupon(userId, parentOrderNo);
        if (released) {
            log.info("[营销中心] 订单关闭解锁优惠券成功, userId: {}, parentOrderNo: {}", userId, parentOrderNo);
        } else {
            log.info("[营销中心] 无需解锁优惠券(未用券或已解锁), userId: {}, parentOrderNo: {}", userId, parentOrderNo);
        }
    }

    /**
     * 查询用户当前可用优惠券列表
     */
    public List<UserCouponEntity> listAvailableCoupons(Long userId) {
        return couponRepository.listAvailableUserCoupons(userId);
    }
}