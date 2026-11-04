package com.mufeng.settlement.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.settlement.infrastructure.persistence.po.ReconciliationDiffPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * T+1 渠道对账差错与分片游标查询 Mapper 接口
 */
@Mapper
public interface ReconciliationDiffMapper extends BaseMapper<ReconciliationDiffPO> {

    /**
     * 按分表后缀 (00 ~ 15) 游标分片拉取指定时间窗口内的平台父订单记录
     */
    @Select("SELECT parent_order_no AS parentOrderNo, total_pay_amount AS totalPayAmount, " +
            "order_status AS orderStatus, channel_trade_no AS channelTradeNo, created_at AS createdAt " +
            "FROM t_parent_order_${tableSuffix} " +
            "WHERE created_at >= #{startTime} AND created_at <= #{endTime}")
    List<Map<String, Object>> selectShardParentOrdersForReconcile(@Param("tableSuffix") String tableSuffix,
                                                                  @Param("startTime") LocalDateTime startTime,
                                                                  @Param("endTime") LocalDateTime endTime);
}