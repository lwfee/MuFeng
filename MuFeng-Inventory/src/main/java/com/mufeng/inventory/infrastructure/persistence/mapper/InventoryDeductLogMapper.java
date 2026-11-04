package com.mufeng.inventory.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.inventory.infrastructure.persistence.po.InventoryDeductLogPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存扣减幂等流水 Mapper 接口
 */
@Mapper
public interface InventoryDeductLogMapper extends BaseMapper<InventoryDeductLogPO> {

    /**
     * CAS 更新流水状态：仅当处于 expectedOldStatus(1-已预扣) 时才更新为 targetStatus(2-已确认 或 3-已释放)
     */
    @Update("UPDATE t_inventory_deduct_log " +
            "SET status = #{targetStatus}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{expectedOldStatus}")
    int updateStatusCas(@Param("id") Long id,
                        @Param("expectedOldStatus") Integer expectedOldStatus,
                        @Param("targetStatus") Integer targetStatus);
}