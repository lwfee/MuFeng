package com.mufeng.marketing.adapter.rpc;

import com.mufeng.api.marketing.dto.PriceCalcReqDTO;
import com.mufeng.api.marketing.dto.PriceCalcRespDTO;
import com.mufeng.api.marketing.facade.MarketingFeignClient;
import com.mufeng.common.core.Result;
import com.mufeng.marketing.application.service.MarketingApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 营销服务内部 OpenFeign 接口实现控制器
 */
@RestController
@RequestMapping("/internal/marketing")
@RequiredArgsConstructor
public class MarketingFeignController implements MarketingFeignClient {

    private final MarketingApplicationService marketingAppService;

    @Override
    @PostMapping("/preview-price")
    public Result<PriceCalcRespDTO> previewPrice(@RequestBody @Valid PriceCalcReqDTO req) {
        return Result.success(marketingAppService.previewPrice(req));
    }

    @Override
    @PostMapping("/lock-coupon-and-calc")
    public Result<PriceCalcRespDTO> lockCouponAndCalc(@RequestBody @Valid PriceCalcReqDTO req) {
        return Result.success(marketingAppService.lockCouponAndCalc(req));
    }

    @Override
    @PostMapping("/release-coupon")
    public Result<Void> releaseCoupon(@RequestParam("userId") Long userId,
                                      @RequestParam("parentOrderNo") Long parentOrderNo) {
        marketingAppService.releaseCoupon(userId, parentOrderNo);
        return Result.success();
    }
}