package com.mufeng.api.marketing.facade;

import com.mufeng.api.marketing.dto.PriceCalcReqDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.common.core.Result;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 营销算价服务对外 OpenFeign 契约接口
 */
@FeignClient(name = "mufeng-marketing", path = "/internal/marketing")
public interface MarketingFeignClient {

    /**
     * 1. 购物车/收银台高频实时试算（纯内存 Caffeine 规则计算，不锁定优惠券）
     */
    @PostMapping("/preview-price")
    Result<PriceCalcRespDTO> previewPrice(@RequestBody @Valid PriceCalcReqDTO req);

    /**
     * 2. 正式提单：锁定用户优惠券并生成精确到分的双层分摊快照
     */
    @PostMapping("/lock-coupon-and-calc")
    Result<PriceCalcRespDTO> lockCouponAndCalc(@RequestBody @Valid PriceCalcReqDTO req);

    /**
     * 3. 订单超时关闭或整单全退：解锁并归还用户优惠券（幂等接口）
     */
    @PostMapping("/release-coupon")
    Result<Void> releaseCoupon(@RequestParam("userId") Long userId,
                               @RequestParam("parentOrderNo") Long parentOrderNo);
}