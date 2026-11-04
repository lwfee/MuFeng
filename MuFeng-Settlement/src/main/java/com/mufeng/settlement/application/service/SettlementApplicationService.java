package com.mufeng.settlement.application.service;

import com.mufeng.api.event.SubOrderSettledEvent;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import com.mufeng.common.lock.DistributedLock;
import com.mufeng.settlement.domain.model.entity.MerchantSettlementBillEntity;
import com.mufeng.settlement.domain.repository.SettlementRepository;
import com.mufeng.settlement.infrastructure.persistence.po.ReconciliationDiffPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 清结算与 T+1 渠道对账应用编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementApplicationService {

    private final SettlementRepository settlementRepository;
    private final GeneSnowflakeIdGenerator idGenerator;

    /**
     * 1. 处理子订单确认收货事件：按清分公式生成商家结算单
     */
    @Transactional(rollbackFor = Exception.class)
    public void generateMerchantSettlementBill(SubOrderSettledEvent event) {
        Long billId = idGenerator.nextId();
        MerchantSettlementBillEntity bill = MerchantSettlementBillEntity.createFromSettledEvent(billId, event);
        boolean saved = settlementRepository.saveSettlementBillIdempotent(bill);
        if (saved) {
            log.info("[清结算中心] 商家结算单生成成功, subOrderNo: {}, merchantId: {}, 买家实付: {}分, 平台补贴: {}分, 平台抽佣: {}分, 商家最终应结净额: {}分",
                    bill.getSubOrderNo(), bill.getMerchantId(), bill.getUserPayAmount(),
                    bill.getPlatformSubsidyAmount(), bill.getPlatformCommissionAmount(), bill.getMerchantNetSettleAmount());
        }
    }

    /**
     * 2. 触发 T+1 渠道对账（支持 XXL-Job 定时调度与运营端手动触发，带分布式锁防并发执行）
     *
     * @param reconcileDate  对账日期
     * @param channelBillMap 第三方渠道对账单流水 (Key: parentOrderNo, Value: 渠道实收金额[分])
     * @return 发现的差错/跨日挂起总笔数
     */
    @DistributedLock(
            prefix = "lock:settlement:reconcile:",
            key = "#reconcileDate.toString()",
            failMessage = "当前日期的对账任务正在执行中，请勿重复触发"
    )
    @Transactional(rollbackFor = Exception.class)
    public int runDailyReconciliation(LocalDate reconcileDate, Map<Long, Long> channelBillMap) {
        log.info("[T+1渠道对账] 开始执行 16 分表游标核对, 账期日: {}, 渠道账单笔数: {}",
                reconcileDate, channelBillMap.size());
        int diffCount = settlementRepository.execute16ShardsReconciliation(reconcileDate, channelBillMap);
        log.info("[T+1渠道对账] 16 分表核对完成, 账期日: {}, 识别差错与跨日缓冲共: {} 笔", reconcileDate, diffCount);
        return diffCount;
    }

    public List<MerchantSettlementBillEntity> listMerchantBills(Long merchantId) {
        return settlementRepository.listBillsByMerchant(merchantId);
    }

    public List<ReconciliationDiffPO> listDiffs(LocalDate reconcileDate, Integer diffType) {
        return settlementRepository.listReconciliationDiffs(reconcileDate, diffType);
    }
}