package com.mufeng.inventory.adapter.web;

import com.mufeng.common.core.Result;
import com.mufeng.inventory.application.service.InventoryApplicationService;
import com.mufeng.inventory.domain.model.entity.SkuInventoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 双模库存中心外部 Web 控制器（通过网关 /api/inventory/** 访问）
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryWebController {

    private final InventoryApplicationService inventoryAppService;

    /**
     * 1. [运营端] 创建或初始化商品日常库存
     */
    @PostMapping("/sku/init")
    public Result<Void> initSku(@RequestBody SkuInventoryEntity entity) {
        inventoryAppService.initOrUpdateSkuInventory(entity);
        return Result.success();
    }

    /**
     * 2. [通用] 查询商品实时双模库存状态（日常可用、日常锁定、秒杀冻结）
     */
    @GetMapping("/sku/detail")
    public Result<SkuInventoryEntity> getSkuDetail(@RequestParam("skuId") Long skuId) {
        return Result.success(inventoryAppService.getSkuInventory(skuId));
    }

    /**
     * 3. [运营端] 秒杀活动库存划拨预热（从日常可用库存划拨至 Redis 独立秒杀池）
     */
    @PostMapping("/seckill/allocate")
    public Result<Void> allocateSeckillStock(@RequestParam("skuId") Long skuId,
                                             @RequestParam("allocateNum") Integer allocateNum) {
        inventoryAppService.allocateSeckillStock(skuId, allocateNum);
        return Result.success();
    }

    /**
     * 4. [运营端] 秒杀活动结束余量回拨（将 Redis 未卖完的库存归还给 MySQL 主库存）
     */
    @PostMapping("/seckill/reclaim")
    public Result<Integer> reclaimSeckillStock(@RequestParam("skuId") Long skuId) {
        return Result.success(inventoryAppService.reclaimSeckillStock(skuId));
    }
}