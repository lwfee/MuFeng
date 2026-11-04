package com.mufeng.settlement.domain.repository;

import com.mufeng.settlement.domain.model.entity.MerchantSettlementBillEntity;
import com.mufeng.settlement.infrastructure.persistence.po.ReconciliationDiffPO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 清结算与渠道对账领域仓储接口
 */
public interface SettlementRepository {

    /**
     * 幂等保存商家清分结算单
     */
    boolean saveSettlementBillIdempotent(MerchantSettlementBillEntity bill);

    /**
     * 查询指定商家的结算账单列表
     */
    List<MerchantSettlementBillEntity> listBillsByMerchant(Long merchantId);

    /**
     * 执行 16 分表游标批处理核对，返回发现的差错与跨日缓冲记录数
     *
     * @param reconcileDate   对账日期
     * @param channelBillMap  第三方支付渠道(微信/支付宝)账单流水 Map (Key: parentOrderNo, Value: 渠道实收金额[分])
     * @return 新增的对账差错/跨日挂起记录条数
     */
    int execute16ShardsReconciliation(LocalDate reconcileDate, Map<Long, Long> channelBillMap);

    /**
     * 查询对账差错与跨日缓冲列表
     */
    List<ReconciliationDiffPO> listReconciliationDiffs(LocalDate reconcileDate, Integer diffType);
}