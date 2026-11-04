package com.mufeng.settlement.adapter.web;

import com.mufeng.common.core.Result;
import com.mufeng.settlement.application.service.SettlementApplicationService;
import com.mufeng.settlement.domain.model.entity.MerchantSettlementBillEntity;
import com.mufeng.settlement.infrastructure.persistence.po.ReconciliationDiffPO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 清结算与对账中心外部 Web 控制器（通过网关 /api/settlement/** 访问）
 */
@RestController
@RequestMapping("/settlement")
@RequiredArgsConstructor
public class SettlementWebController {

    private final SettlementApplicationService settlementAppService;

    /**
     * 1. [商家端/财务端] 查询指定商家的清分结算账单明细列表
     */
    @GetMapping("/bill/list")
    public Result<List<MerchantSettlementBillEntity>> listMerchantBills(@RequestParam("merchantId") Long merchantId) {
        return Result.success(settlementAppService.listMerchantBills(merchantId));
    }

    /**
     * 2. [财务端/对账演练] 传入模拟的微信/支付宝渠道账单 Map，触发 16 分表游标对账与差错拦截
     *
     * @param reconcileDate  对账日期 (格式: yyyy-MM-dd)
     * @param channelBillMap 请求体 JSON 示例: {"1829384756102938": 27000} (Key为父单号, Value为渠道实收分)
     */
    @PostMapping("/reconcile/trigger")
    public Result<Integer> triggerReconciliation(
            @RequestParam("reconcileDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reconcileDate,
            @RequestBody Map<Long, Long> channelBillMap) {
        return Result.success(settlementAppService.runDailyReconciliation(reconcileDate, channelBillMap));
    }

    /**
     * 3. [财务端] 查询对账差错与日切跨天缓冲池记录
     */
    @GetMapping("/reconcile/diffs")
    public Result<List<ReconciliationDiffPO>> listDiffs(
            @RequestParam(value = "reconcileDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reconcileDate,
            @RequestParam(value = "diffType", required = false) Integer diffType) {
        return Result.success(settlementAppService.listDiffs(reconcileDate, diffType));
    }
}