package com.mufeng.marketing.adapter.web;

import com.mufeng.api.marketing.dto.PriceCalcReqDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.common.core.Result;
import com.mufeng.marketing.application.service.MarketingApplicationService;
import com.mufeng.marketing.domain.model.entity.CouponTemplateEntity;
import com.mufeng.marketing.domain.model.entity.UserCouponEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 营销算价中心外部 Web 控制器（通过网关 /api/marketing/** 访问）
 */
@RestController
@RequestMapping("/marketing")
@RequiredArgsConstructor
public class MarketingWebController {

    private final MarketingApplicationService marketingAppService;

    /**
     * 1. [运营端] 创建优惠券模板（支持配置平台补贴万分比 platformSubsidyRatio）
     */
    @PostMapping("/template/create")
    public Result<Long> createTemplate(@RequestBody CouponTemplateEntity template) {
        return Result.success(marketingAppService.createCouponTemplate(template));
    }

    /**
     * 2. [买家端] 领取优惠券
     */
    @PostMapping("/coupon/receive")
    public Result<Long> receiveCoupon(@RequestParam("userId") Long userId,
                                      @RequestParam("templateId") Long templateId) {
        return Result.success(marketingAppService.receiveCoupon(userId, templateId));
    }

    /**
     * 3. [买家端] 查询个人可用优惠券列表
     */
    @GetMapping("/coupon/available")
    public Result<List<UserCouponEntity>> listAvailableCoupons(@RequestParam("userId") Long userId) {
        return Result.success(marketingAppService.listAvailableCoupons(userId));
    }

    /**
     * 4. [买家端] 购物车/收银台实时试算与出资分摊预览（8000 QPS 内存级响应）
     */
    @PostMapping("/cart/preview")
    public Result<PriceCalcRespDTO> previewCartPrice(@RequestBody @Valid PriceCalcReqDTO req) {
        return Result.success(marketingAppService.previewPrice(req));
    }
}