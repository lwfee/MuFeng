package com.mufeng.inventory.application.service;

import com.mufeng.api.inventory.dto.InventoryDeductReqDTO;
import com.mufeng.api.inventory.dto.SkuDeductItemDTO;
import com.mufeng.inventory.domain.model.entity.SkuInventoryEntity;
import com.mufeng.inventory.domain.model.vo.DeductModeEnum;
import com.mufeng.inventory.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 双模库存应用编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryApplicationService {

    private final InventoryRepository inventoryRepository;

    /**
     * 1. 初始化或修改商品日常库存
     */
    @Transactional(rollbackFor = Exception.class)
    public void initOrUpdateSkuInventory(SkuInventoryEntity entity) {
        inventoryRepository.saveOrUpdateSku(entity);
    }

    /**
     * 2. 查询商品双模库存详情
     */
    public SkuInventoryEntity getSkuInventory(Long skuId) {
        return inventoryRepository.getBySkuId(skuId);
    }

    /**
     * 3. 运营预热：将日常库存划拨至秒杀活动独立库存并写入 Redis
     */
    @Transactional(rollbackFor = Exception.class)
    public void allocateSeckillStock(Long skuId, int allocateNum) {
        inventoryRepository.allocateSeckillStock(skuId, allocateNum);
        log.info("[双模库存] 秒杀库存划拨预热完成, skuId: {}, 划拨数量: {}", skuId, allocateNum);
    }

    /**
     * 4. 活动结束：将 Redis 剩余未卖完的秒杀库存回拨归还至 MySQL 日常可用库存
     */
    @Transactional(rollbackFor = Exception.class)
    public int reclaimSeckillStock(Long skuId) {
        int reclaimed = inventoryRepository.reclaimSeckillStock(skuId);
        log.info("[双模库存] 秒杀结束余量回拨完成, skuId: {}, 回拨数量: {}", skuId, reclaimed);
        return reclaimed;
    }

    /**
     * 5. 提单批量预扣库存（具备 DB 本地事务回滚 + Redis 秒杀预扣异常逆向补偿机制）
     */
    @Transactional(rollbackFor = Exception.class)
    public void tryDeductStock(InventoryDeductReqDTO req) {
        List<SkuDeductItemDTO> deductedSeckillItems = new ArrayList<>();
        try {
            for (SkuDeductItemDTO item : req.getDeductItems()) {
                inventoryRepository.deductSingleItem(req.getParentOrderNo(), req.getUserId(), item);
                if (DeductModeEnum.isSeckill(item.getDeductMode())) {
                    deductedSeckillItems.add(item);
                }
            }
            log.info("[双模库存] 订单库存预扣成功, parentOrderNo: {}, userId: {}", req.getParentOrderNo(), req.getUserId());
        } catch (Exception e) {
            // 若批次中后续商品扣减失败，立即逆向补偿回滚本批次已预扣成功的 Redis 秒杀库存
            for (SkuDeductItemDTO seckillItem : deductedSeckillItems) {
                try {
                    inventoryRepository.compensateSeckillRedis(
                            seckillItem.getSkuId(), req.getUserId(), seckillItem.getDeductQuantity());
                } catch (Exception ex) {
                    log.error("[双模库存] 异常补偿释放 Redis 秒杀库存失败, skuId: {}", seckillItem.getSkuId(), ex);
                }
            }
            throw e;
        }
    }

    /**
     * 6. 支付成功：确认实扣库存
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmDeductStock(Long parentOrderNo) {
        inventoryRepository.confirmOrderStock(parentOrderNo);
        log.info("[双模库存] 订单支付成功实扣确认完成, parentOrderNo: {}", parentOrderNo);
    }

    /**
     * 7. 订单超时关闭或取消：幂等释放回补双模库存
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(Long parentOrderNo, Long userId) {
        inventoryRepository.releaseOrderStock(parentOrderNo, userId);
        log.info("[双模库存] 订单关闭释放回补库存完成, parentOrderNo: {}", parentOrderNo);
    }
}