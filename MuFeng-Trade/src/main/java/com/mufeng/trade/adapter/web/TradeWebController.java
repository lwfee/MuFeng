package com.mufeng.trade.adapter.web;

import com.mufeng.common.core.Result;
import com.mufeng.trade.application.dto.CreateOrderReqDTO;
import com.mufeng.trade.application.service.TradeApplicationService;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;
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
 * 交易履约中心外部 Web 控制器（通过网关 /api/trade/** 访问）
 */
@RestController
@RequestMapping("/trade")
@RequiredArgsConstructor
public class TradeWebController {

    private final TradeApplicationService tradeAppService;

    /**
     * 1. [买家端] 收银台合并提单（自动跨店拆单、锁券算价、预扣双模库存、路由落入 16 分表）
     */
    @PostMapping("/order/create")
    public Result<OrderAggregate> createOrder(@RequestBody @Valid CreateOrderReqDTO req) {
        return Result.success(tradeAppService.createOrder(req));
    }

    /**
     * 2. [支付回调] 模拟微信/支付宝支付成功通知（激活名下所有店铺子单为待发货）
     */
    @PostMapping("/order/pay-callback")
    public Result<Void> payCallback(@RequestParam("parentOrderNo") Long parentOrderNo,
                                    @RequestParam(value = "payChannel", defaultValue = "1") Integer payChannel,
                                    @RequestParam("channelTradeNo") String channelTradeNo) {
        tradeAppService.onPaySuccess(parentOrderNo, payChannel, channelTradeNo);
        return Result.success();
    }

    /**
     * 3. [商家端] 店铺子订单独立发货
     */
    @PostMapping("/sub-order/deliver")
    public Result<Void> deliverSubOrder(@RequestParam("subOrderNo") Long subOrderNo) {
        tradeAppService.deliverSubOrder(subOrderNo);
        return Result.success();
    }

    /**
     * 4. [买家端] 店铺子订单确认收货（自动异步触发商家补贴与抽佣清结算）
     */
    @PostMapping("/sub-order/confirm")
    public Result<Void> confirmReceive(@RequestParam("subOrderNo") Long subOrderNo) {
        tradeAppService.confirmReceiveSubOrder(subOrderNo);
        return Result.success();
    }

    /**
     * 5. [买家售后] 子订单单品逆向退货退款（按建单分摊快照实付金额退回，若全退完自动解券）
     */
    @PostMapping("/sub-order/refund-item")
    public Result<Long> refundItem(@RequestParam("subOrderNo") Long subOrderNo,
                                   @RequestParam("skuId") Long skuId) {
        return Result.success(tradeAppService.refundSubOrderItem(subOrderNo, skuId));
    }

    /**
     * 6. [查询] 根据父订单号查询聚合详情（直接从订单号末4位提取基因路由单表，0广播）
     */
    @GetMapping("/order/detail")
    public Result<OrderAggregate> getOrderDetail(@RequestParam("parentOrderNo") Long parentOrderNo) {
        return Result.success(tradeAppService.getOrderDetail(parentOrderNo));
    }

    /**
     * 7. [买家端] 查询个人全部店铺子订单列表（精准路由至 userId 所属分表）
     */
    @GetMapping("/sub-order/list")
    public Result<List<SubOrderEntity>> listUserSubOrders(@RequestParam("userId") Long userId) {
        return Result.success(tradeAppService.listUserSubOrders(userId));
    }
}