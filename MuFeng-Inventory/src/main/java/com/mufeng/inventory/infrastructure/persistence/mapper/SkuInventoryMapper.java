package com.mufeng.inventory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.inventory.infrastructure.persistence.po.SkuInventoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品双模库存 Mapper 接口
 */
@Mapper
public interface SkuInventoryMapper extends BaseMapper<SkuInventoryPO> {

    /**
     * 1. [普通商品] 提单预占：available_stock 减少，locked_stock 增加
     */
    @Update("UPDATE t_sku_inventory " +
            "SET available_stock = available_stock - #{num}, locked_stock = locked_stock + #{num}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND available_stock >= #{num}")
    int tryLockNormalStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 2. [普通商品] 支付实扣确认：locked_stock 减少
     */
    @Update("UPDATE t_sku_inventory " +
            "SET locked_stock = locked_stock - #{num}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND locked_stock >= #{num}")
    int confirmNormalStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 3. [普通商品] 关单回滚释放：locked_stock 减少，available_stock 增加
     */
    @Update("UPDATE t_sku_inventory " +
            "SET available_stock = available_stock + #{num}, locked_stock = locked_stock - #{num}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND locked_stock >= #{num}")
    int releaseNormalStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 4. [秒杀商品] 活动库存划拨冻结：available_stock 转移至 activity_frozen_stock
     */
    @Update("UPDATE t_sku_inventory " +
            "SET available_stock = available_stock - #{num}, activity_frozen_stock = activity_frozen_stock + #{num}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND available_stock >= #{num}")
    int allocateSeckillStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 5. [秒杀商品] 支付实扣确认：activity_frozen_stock 减少
     */
    @Update("UPDATE t_sku_inventory " +
            "SET activity_frozen_stock = activity_frozen_stock - #{num}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND activity_frozen_stock >= #{num}")
    int confirmSeckillStock(@Param("skuId") Long skuId, @Param("num") int num);

    /**
     * 6. [秒杀商品] 活动结束余量回拨：将未售完的 activity_frozen_stock 归还至 available_stock
     */
    @Update("UPDATE t_sku_inventory " +
            "SET available_stock = available_stock + #{remainNum}, activity_frozen_stock = activity_frozen_stock - #{remainNum}, version = version + 1 " +
            "WHERE sku_id = #{skuId} AND activity_frozen_stock >= #{remainNum}")
    int reclaimSeckillStock(@Param("skuId") Long skuId, @Param("remainNum") int remainNum);
}