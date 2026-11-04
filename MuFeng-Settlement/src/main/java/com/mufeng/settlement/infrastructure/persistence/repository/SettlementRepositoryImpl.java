package com.mufeng.settlement.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mufeng.settlement.domain.model.entity.MerchantSettlementBillEntity;
import com.mufeng.settlement.domain.model.vo.ReconcileDiffTypeEnum;
import com.mufeng.settlement.domain.repository.SettlementRepository;
import com.mufeng.settlement.infrastructure.persistence.mapper.MerchantSettlementBillMapper;
import com.mufeng.settlement.infrastructure.persistence.mapper.ReconciliationDiffMapper;
import com.mufeng.settlement.infrastructure.persistence.po.MerchantSettlementBillPO;
import com.mufeng.settlement.infrastructure.persistence.po.ReconciliationDiffPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 清结算与对账领域仓储实现类
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SettlementRepositoryImpl implements SettlementRepository {

    private final MerchantSettlementBillMapper settlementBillMapper;
    private final ReconciliationDiffMapper reconciliationDiffMapper;

    @Override
    public boolean saveSettlementBillIdempotent(MerchantSettlementBillEntity bill) {
        MerchantSettlementBillPO po = new MerchantSettlementBillPO();
        po.setId(bill.getId());
        po.setSubOrderNo(bill.getSubOrderNo());
        po.setMerchantId(bill.getMerchantId());
        po.setUserPayAmount(bill.getUserPayAmount());
        po.setPlatformSubsidyAmount(bill.getPlatformSubsidyAmount());
        po.setCommissionRate(bill.getCommissionRate());
        po.setPlatformCommissionAmount(bill.getPlatformCommissionAmount());
        po.setMerchantNetSettleAmount(bill.getMerchantNetSettleAmount());
        po.setBillStatus(bill.getBillStatus());
        po.setSettleDate(bill.getSettleDate());

        try {
            settlementBillMapper.insert(po);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_sub_order 兜底防重，保证 MQ 重复投递时绝对幂等
            log.info("[清结算仓储] 子订单结算单已存在(幂等跳过), subOrderNo: {}", bill.getSubOrderNo());
            return false;
        }
    }

    @Override
    public List<MerchantSettlementBillEntity> listBillsByMerchant(Long merchantId) {
        List<MerchantSettlementBillPO> list = settlementBillMapper.selectList(
                new LambdaQueryWrapper<MerchantSettlementBillPO>()
                        .eq(MerchantSettlementBillPO::getMerchantId, merchantId)
                        .orderByDesc(MerchantSettlementBillPO::getCreatedAt)
        );
        return list.stream().map(po -> MerchantSettlementBillEntity.builder()
                .id(po.getId())
                .subOrderNo(po.getSubOrderNo())
                .merchantId(po.getMerchantId())
                .userPayAmount(po.getUserPayAmount())
                .platformSubsidyAmount(po.getPlatformSubsidyAmount())
                .commissionRate(po.getCommissionRate())
                .platformCommissionAmount(po.getPlatformCommissionAmount())
                .merchantNetSettleAmount(po.getMerchantNetSettleAmount())
                .billStatus(po.getBillStatus())
                .settleDate(po.getSettleDate())
                .build()).collect(Collectors.toList());
    }

    @Override
    public int execute16ShardsReconciliation(LocalDate reconcileDate, Map<Long, Long> channelBillMap) {
        LocalDateTime startTime = reconcileDate.atStartOfDay();
        LocalDateTime endTime = reconcileDate.atTime(LocalTime.MAX);
        Map<Long, Long> remainingChannelMap = new HashMap<>(channelBillMap);
        int diffCount = 0;

        // 1. 逐片扫描 00 ~ 15 共 16 张父订单分表（流式批处理，内存占用极低）
        for (int i = 0; i < 16; i++) {
            String suffix = i < 10 ? "0" + i : String.valueOf(i);
            List<Map<String, Object>> shardOrders = reconciliationDiffMapper.selectShardParentOrdersForReconcile(
                    suffix, startTime, endTime);

            for (Map<String, Object> row : shardOrders) {
                Long parentOrderNo = ((Number) row.get("parentOrderNo")).longValue();
                long platformPayAmount = ((Number) row.get("totalPayAmount")).longValue();
                int orderStatus = ((Number) row.get("orderStatus")).intValue();
                String channelTradeNo = (String) row.get("channelTradeNo");
                LocalDateTime createdAt = (LocalDateTime) row.get("createdAt");

                Long channelAmount = remainingChannelMap.remove(parentOrderNo);
                boolean platformPaid = (orderStatus == 20 || orderStatus == 40);

                // 场景 A：平台记录已支付，但渠道账单无此记录（短款 或 跨日缓冲）
                if (platformPaid && channelAmount == null) {
                    boolean isCrossDay = isInCrossDayWindow(createdAt);
                    recordDiffAndFreezeIfNeeded(
                            reconcileDate,
                            parentOrderNo,
                            channelTradeNo,
                            isCrossDay ? ReconcileDiffTypeEnum.CROSS_DAY_BUFFER : ReconcileDiffTypeEnum.SHORT_BILL,
                            platformPayAmount,
                            0L,
                            isCrossDay ? "日切窗口(23:55-00:05)跨天挂起，待次日自动复核" : "短款告警：平台显示已支付，渠道账单缺失"
                    );
                    diffCount++;
                }
                // 场景 B：平台与渠道均已支付，但实收金额不一致（金额差错）
                else if (platformPaid && !channelAmount.equals(platformPayAmount)) {
                    recordDiffAndFreezeIfNeeded(
                            reconcileDate,
                            parentOrderNo,
                            channelTradeNo,
                            ReconcileDiffTypeEnum.AMOUNT_MISMATCH,
                            platformPayAmount,
                            channelAmount,
                            "金额不符告警：平台实付[" + platformPayAmount + "分] vs 渠道入账[" + channelAmount + "分]"
                    );
                    diffCount++;
                }
                // 场景 C：平台记录未支付/已关单，但渠道账单却收了钱（长款差错，需触发掉单补单或原路退回）
                else if (!platformPaid && channelAmount != null) {
                    recordDiffAndFreezeIfNeeded(
                            reconcileDate,
                            parentOrderNo,
                            channelTradeNo,
                            ReconcileDiffTypeEnum.LONG_BILL,
                            0L,
                            channelAmount,
                            "长款告警：渠道已收款[" + channelAmount + "分]，但平台订单处于未支付状态"
                    );
                    diffCount++;
                }
            }
        }

        // 2. 检查 remainingChannelMap 中剩余的记录（渠道有账单、但平台 16 张分表连订单号都不存在 -> 典型长款）
        for (Map.Entry<Long, Long> entry : remainingChannelMap.entrySet()) {
            recordDiffAndFreezeIfNeeded(
                    reconcileDate,
                    entry.getKey(),
                    "CH_UNKNOWN",
                    ReconcileDiffTypeEnum.LONG_BILL,
                    0L,
                    entry.getValue(),
                    "长款告警：渠道存在入账流水，平台库无此父订单记录"
            );
            diffCount++;
        }

        return diffCount;
    }

    @Override
    public List<ReconciliationDiffPO> listReconciliationDiffs(LocalDate reconcileDate, Integer diffType) {
        LambdaQueryWrapper<ReconciliationDiffPO> wrapper = new LambdaQueryWrapper<>();
        if (reconcileDate != null) {
            wrapper.eq(ReconciliationDiffPO::getReconcileDate, reconcileDate);
        }
        if (diffType != null) {
            wrapper.eq(ReconciliationDiffPO::getDiffType, diffType);
        }
        wrapper.orderByDesc(ReconciliationDiffPO::getCreatedAt);
        return reconciliationDiffMapper.selectList(wrapper);
    }

    /**
     * 判断订单是否处于 23:55:00 ~ 00:05:00 的午夜日切缓冲窗口
     */
    private boolean isInCrossDayWindow(LocalDateTime createdAt) {
        if (createdAt == null) {
            return false;
        }
        LocalTime time = createdAt.toLocalTime();
        return time.isAfter(LocalTime.of(23, 55)) || time.isBefore(LocalTime.of(0, 5));
    }

    private void recordDiffAndFreezeIfNeeded(LocalDate reconcileDate,
                                             Long parentOrderNo,
                                             String channelTradeNo,
                                             ReconcileDiffTypeEnum diffType,
                                             long platformAmount,
                                             long channelAmount,
                                             String remark) {
        ReconciliationDiffPO diffPO = new ReconciliationDiffPO();
        diffPO.setReconcileDate(reconcileDate);
        diffPO.setParentOrderNo(parentOrderNo);
        diffPO.setChannelTradeNo(channelTradeNo);
        diffPO.setDiffType(diffType.getCode());
        diffPO.setPlatformAmount(platformAmount);
        diffPO.setChannelAmount(channelAmount);
        diffPO.setHandleStatus(diffType == ReconcileDiffTypeEnum.CROSS_DAY_BUFFER ? 0 : 1);
        diffPO.setRemark(remark);

        try {
            reconciliationDiffMapper.insert(diffPO);
        } catch (DuplicateKeyException ignored) {
            // 同一日期同一父订单幂等更新
        }

        // 若为真实短款或金额不符，立即冻结该父订单下所有商家的待打款结算单（防资损护城河）
        if (diffType == ReconcileDiffTypeEnum.SHORT_BILL || diffType == ReconcileDiffTypeEnum.AMOUNT_MISMATCH) {
            int frozenCount = settlementBillMapper.freezeSettlementBillsByParentOrder(parentOrderNo);
            log.warn("[对账资损拦截] 发现差错订单 parentOrderNo: {}, 类型: {}, 已自动冻结 {} 张关联商家结算单!",
                    parentOrderNo, diffType.getDesc(), frozenCount);
        }
    }
}