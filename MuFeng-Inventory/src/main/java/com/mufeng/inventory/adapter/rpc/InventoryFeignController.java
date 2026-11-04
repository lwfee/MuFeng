package com.mufeng.inventory.adapter.rpc;

import com.mufeng.api.inventory.dto.InventoryDeductReqDTO;
import com.mufeng.api.inventory.facade.InventoryFeignClient;
import com.mufeng.common.core.Result;
import com.mufeng.inventory.application.service.InventoryApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 双模库存内部 OpenFeign 契约实现控制器
 */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InventoryFeignController implements InventoryFeignClient {

    private final InventoryApplicationService inventoryAppService;

    @Override
    @PostMapping("/try-deduct")
    public Result<Void> tryDeductStock(@RequestBody @Valid InventoryDeductReqDTO req) {
        inventoryAppService.tryDeductStock(req);
        return Result.success();
    }

    @Override
    @PostMapping("/confirm-deduct")
    public Result<Void> confirmDeductStock(@RequestParam("parentOrderNo") Long parentOrderNo) {
        inventoryAppService.confirmDeductStock(parentOrderNo);
        return Result.success();
    }

    @Override
    @PostMapping("/release-stock")
    public Result<Void> releaseStock(@RequestParam("parentOrderNo") Long parentOrderNo) {
        // 若通过 Feign 直接根据父单号释放普通库存，userId 传 0L 即可（秒杀释放另有 MQ 携带完整 userId 触发）
        inventoryAppService.releaseStock(parentOrderNo, 0L);
        return Result.success();
    }
}