package com.mufeng.api.inventory.facade;

import com.mufeng.api.inventory.dto.InventoryDeductReqDTO;
import com.mufeng.common.core.Result;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 双模库存服务对外 OpenFeign 契约接口
 */
@FeignClient(name = "mufeng-inventory", path = "/internal/inventory")
public interface InventoryFeignClient {

    /**
     * 1. 提单预扣库存（自动按 deductMode 路由至 MySQL 行锁或 Redis Lua 划拨库存，并记录幂等流水）
     */
    @PostMapping("/try-deduct")
    Result<Void> tryDeductStock(@RequestBody @Valid InventoryDeductReqDTO req);

    /**
     * 2. 支付成功实扣确认（将预占锁定库存转为最终实扣状态）
     */
    @PostMapping("/confirm-deduct")
    Result<Void> confirmDeductStock(@RequestParam("parentOrderNo") Long parentOrderNo);

    /**
     * 3. 订单超时未支付或取消：回滚并释放已预占的库存（支持普通库存与秒杀Redis库存双模回补，天然幂等）
     */
    @PostMapping("/release-stock")
    Result<Void> releaseStock(@RequestParam("parentOrderNo") Long parentOrderNo);
}