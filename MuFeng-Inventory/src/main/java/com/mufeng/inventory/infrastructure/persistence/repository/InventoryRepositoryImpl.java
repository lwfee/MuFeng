package com.mufeng.inventory.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mufeng.api.inventory.dto.SkuDeductItemDTO;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.inventory.domain.model.entity.SkuInventoryEntity;
import com.mufeng.inventory.domain.model.vo.DeductLogStatusEnum;
import com.mufeng.inventory.domain.model.vo.DeductModeEnum;
import com.mufeng.inventory.domain.repository.InventoryRepository;
import com.mufeng.inventory.infrastructure.cache.SeckillStockRedisExecutor;
import com.mufeng.inventory.infrastructure.persistence.mapper.InventoryDeductLogMapper;
import com.mufeng.inventory.infrastructure.persistence.mapper.SkuInventoryMapper;
import com.mufeng.inventory.infrastructure.persistence.po.InventoryDeductLogPO;
import com.mufeng.inventory.infrastructure.persistence.po.SkuInventoryPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 双模库存仓储实现类（整合 MySQL 行锁、幂等流水表与 Redis Lua 秒杀执行器）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryRepositoryImpl implements InventoryRepository {

    private final SkuInventoryMapper skuInventoryMapper;
    private final InventoryDeductLogMapper deductLogMapper;
    private final SeckillStockRedisExecutor seckillRedisExecutor;

    @Override
    public void saveOrUpdateSku(SkuInventoryEntity entity) {
        SkuInventoryPO existing = skuInventoryMapper.selectById(entity.getSkuId());
        SkuInventoryPO po = new SkuInventoryPO();
        po.setSkuId(entity.getSkuId());
        po.setMerchantId(entity.getMerchantId());
        po.setSkuName(entity.getSkuName());
        po.setSalePrice(entity.getSalePrice());
        po.setAvailableStock(entity.getAvailableStock());
        po.setLockedStock(entity.getLockedStock() == null ? 0 : entity.getLockedStock());
        po.setActivityFrozenStock(entity.getActivityFrozenStock() == null ? 0 : entity.getActivityFrozenStock());

        if (existing == null) {
            po.setVersion(0);
            skuInventoryMapper.insert(po);
        } else {
            skuInventoryMapper.updateById(po);
        }
    }

    @Override
    public SkuInventoryEntity getBySkuId(Long skuId) {
        SkuInventoryPO po = skuInventoryMapper.selectById(skuId);
        if (po == null) {
            return null;
        }
        return SkuInventoryEntity.builder()
                .skuId(po.getSkuId())
                .merchantId(po.getMerchantId())
                .skuName(po.getSkuName())
                .salePrice(po.getSalePrice())
                .availableStock(po.getAvailableStock())
                .lockedStock(po.getLockedStock())
                .activityFrozenStock(po.getActivityFrozenStock())
                .build();
    }

    @Override
    public void allocateSeckillStock(Long skuId, int allocateNum) {
        int rows = skuInventoryMapper.allocateSeckillStock(skuId, allocateNum);
        if (rows <= 0) {
            throw new BizException(ErrorCodeEnum.STOCK_ALLOCATION_FAILED);
        }
        // MySQL 划拨冻结成功后，同步将秒杀库存预热至 Redis
        seckillRedisExecutor.initOrIncrementSeckillStock(skuId, allocateNum);
    }

    @Override
    public int reclaimSeckillStock(Long skuId) {
        int remainInRedis = seckillRedisExecutor.extractRemainingStockForReclaim(skuId);
        if (remainInRedis > 0) {
            skuInventoryMapper.reclaimSeckillStock(skuId, remainInRedis);
        }
        return remainInRedis;
    }

    @Override
    public void deductSingleItem(Long parentOrderNo, Long userId, SkuDeductItemDTO item) {
        // 1. 优先插入唯一索引幂等流水记录 (uk_order_sku: sub_order_no + sku_id)，防网络重试重复扣减
        InventoryDeductLogPO logPO = new InventoryDeductLogPO();
        logPO.setParentOrderNo(parentOrderNo);
        logPO.setSubOrderNo(item.getSubOrderNo());
        logPO.setSkuId(item.getSkuId());
        logPO.setDeductMode(item.getDeductMode());
        logPO.setDeductNum(item.getDeductQuantity());
        logPO.setStatus(DeductLogStatusEnum.LOCKED.getCode());
        deductLogMapper.insert(logPO);

        // 2. 按模式执行双模库存扣减
        if (DeductModeEnum.isSeckill(item.getDeductMode())) {
            // 模式 2：秒杀商品走 Caffeine 售罄标记 + Redis 单 Key Lua 原子预扣
            seckillRedisExecutor.executeSeckillDeduct(item.getSkuId(), userId, item.getDeductQuantity());
        } else {
            // 模式 1：普通商品直接走 MySQL 行锁预占
            int rows = skuInventoryMapper.tryLockNormalStock(item.getSkuId(), item.getDeductQuantity());
            if (rows <= 0) {
                throw new BizException(ErrorCodeEnum.NORMAL_STOCK_INSUFFICIENT,
                        "商品 SKU[" + item.getSkuId() + "] 库存不足");
            }
        }
    }

    @Override
    public void compensateSeckillRedis(Long skuId, Long userId, int deductNum) {
        seckillRedisExecutor.executeSeckillRelease(skuId, userId, deductNum);
    }

    @Override
    public void confirmOrderStock(Long parentOrderNo) {
        List<InventoryDeductLogPO> logs = deductLogMapper.selectList(
                new LambdaQueryWrapper<InventoryDeductLogPO>()
                        .eq(InventoryDeductLogPO::getParentOrderNo, parentOrderNo)
                        .eq(InventoryDeductLogPO::getStatus, DeductLogStatusEnum.LOCKED.getCode())
        );

        for (InventoryDeductLogPO logPO : logs) {
            // CAS 更新流水状态为 2-已实扣确认
            int updated = deductLogMapper.updateStatusCas(
                    logPO.getId(),
                    DeductLogStatusEnum.LOCKED.getCode(),
                    DeductLogStatusEnum.CONFIRMED.getCode()
            );
            if (updated > 0) {
                if (DeductModeEnum.isSeckill(logPO.getDeductMode())) {
                    skuInventoryMapper.confirmSeckillStock(logPO.getSkuId(), logPO.getDeductNum());
                } else {
                    skuInventoryMapper.confirmNormalStock(logPO.getSkuId(), logPO.getDeductNum());
                }
            }
        }
    }

    @Override
    public void releaseOrderStock(Long parentOrderNo, Long userId) {
        List<InventoryDeductLogPO> logs = deductLogMapper.selectList(
                new LambdaQueryWrapper<InventoryDeductLogPO>()
                        .eq(InventoryDeductLogPO::getParentOrderNo, parentOrderNo)
                        .eq(InventoryDeductLogPO::getStatus, DeductLogStatusEnum.LOCKED.getCode())
        );

        for (InventoryDeductLogPO logPO : logs) {
            // CAS 更新流水状态为 3-已回滚释放（保证即使 MQ 与 Feign 并发触发关单，也绝对只回补一次）
            int updated = deductLogMapper.updateStatusCas(
                    logPO.getId(),
                    DeductLogStatusEnum.LOCKED.getCode(),
                    DeductLogStatusEnum.RELEASED.getCode()
            );
            if (updated > 0) {
                if (DeductModeEnum.isSeckill(logPO.getDeductMode())) {
                    seckillRedisExecutor.executeSeckillRelease(logPO.getSkuId(), userId, logPO.getDeductNum());
                } else {
                    skuInventoryMapper.releaseNormalStock(logPO.getSkuId(), logPO.getDeductNum());
                }
            }
        }
    }
}