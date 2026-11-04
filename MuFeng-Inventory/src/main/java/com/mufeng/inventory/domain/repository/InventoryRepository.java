package com.mufeng.inventory.domain.repository;

import com.mufeng.api.inventory.dto.SkuDeductItemDTO;
import com.mufeng.inventory.domain.model.entity.SkuInventoryEntity;

/**
 * 双模库存领域仓储接口
 */
public interface InventoryRepository {

    /**
     * 初始化或更新商品库存信息
     */
    void saveOrUpdateSku(SkuInventoryEntity entity);

    /**
     * 查询商品库存详情
     */
    SkuInventoryEntity getBySkuId(Long skuId);

    /**
     * 秒杀预热：从日常可用库存中划拨指定数量至活动冻结库存，并写入 Redis
     */
    void allocateSeckillStock(Long skuId, int allocateNum);

    /**
     * 秒杀结束：将 Redis 中未售完的剩余划拨库存回拨至 MySQL 日常可用库存
     */
    int reclaimSeckillStock(Long skuId);

    /**
     * 执行单个 SKU 的双模预扣（含幂等流水落库）
     */
    void deductSingleItem(Long parentOrderNo, Long userId, SkuDeductItemDTO item);

    /**
     * 补偿回滚 Redis 秒杀预扣（当同笔订单内其他 SKU 扣减失败触发本地事务回滚时调用）
     */
    void compensateSeckillRedis(Long skuId, Long userId, int deductNum);

    /**
     * 支付成功：将父订单下所有预扣流水转为实扣确认状态，并扣减对应冻结/锁定库存
     */
    void confirmOrderStock(Long parentOrderNo);

    /**
     * 关单/取消：将父订单下所有预扣流水转为已释放状态，并回补 DB 或 Redis 库存（天然幂等）
     */
    void releaseOrderStock(Long parentOrderNo, Long userId);
}