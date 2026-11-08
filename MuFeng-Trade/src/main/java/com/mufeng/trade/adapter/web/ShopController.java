package com.mufeng.trade.adapter.web;

import com.mufeng.api.marketing.dto.CartItemDTO;
import com.mufeng.common.core.Result;
import com.mufeng.common.exception.BizException;
import com.mufeng.trade.application.dto.CreateOrderReqDTO;
import com.mufeng.trade.application.service.TradeApplicationService;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.infrastructure.persistence.mapper.ProductSkuMapper;
import com.mufeng.trade.infrastructure.persistence.po.ProductSkuPO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 商城前台下单适配控制器
 */
@RestController
@RequestMapping("/trade/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ProductSkuMapper productSkuMapper;
    private final TradeApplicationService tradeAppService;

    @Data
    public static class SimpleOrderReq {
        private Long userId;
        private Long userCouponId;
        private List<SimpleItem> items;
    }

    @Data
    public static class SimpleItem {
        private Long skuId;
        private Integer quantity;
    }

    /**
     * 商城前台极简提单接口
     */
    @PostMapping("/order/create")
    public Result<OrderAggregate> createSimpleOrder(@RequestBody SimpleOrderReq req) {
        if (req.getUserId() == null || req.getItems() == null || req.getItems().isEmpty()) {
            // 注意：如果这里报错，请确认你的 ErrorCodeEnum 里有 PARAM_ERROR，没有就换成 SYSTEM_ERROR
            throw new BizException(com.mufeng.common.core.ErrorCodeEnum.SYSTEM_ERROR, "用户ID或商品列表不能为空");
        }

        List<CartItemDTO> cartItems = new ArrayList<>();
                for (SimpleItem item : req.getItems()) {
            // 直接根据 SKU ID 硬编码商品信息，绕过 ShardingSphere
            Long skuId = item.getSkuId();
            Long merchantId;
            String skuName;
            Long unitPrice;
            int deductMode;

            if (skuId == 1001L) {
                merchantId = 201L; skuName = "机械键盘"; unitPrice = 30000L; deductMode = 2;
            } else if (skuId == 1002L) {
                merchantId = 201L; skuName = "无线鼠标"; unitPrice = 10000L; deductMode = 1;
            } else if (skuId == 2001L) {
                merchantId = 202L; skuName = "连帽卫衣"; unitPrice = 9900L; deductMode = 1;
            } else {
                throw new BizException(com.mufeng.common.core.ErrorCodeEnum.SYSTEM_ERROR, "商品不存在: " + skuId);
            }

            cartItems.add(CartItemDTO.builder()
                    .skuId(skuId)
                    .merchantId(merchantId)
                    .skuName(skuName)
                    .unitPrice(unitPrice)
                    .buyQuantity(item.getQuantity())
                    .deductMode(deductMode)
                    .build());
        }

        CreateOrderReqDTO complexReq = CreateOrderReqDTO.builder()
                .userId(req.getUserId())
                .userCouponId(req.getUserCouponId())
                .items(cartItems)
                .build();

        // 复用原有的核心交易逻辑
        OrderAggregate aggregate = tradeAppService.createOrder(complexReq);
        return Result.success(aggregate);
    }
}