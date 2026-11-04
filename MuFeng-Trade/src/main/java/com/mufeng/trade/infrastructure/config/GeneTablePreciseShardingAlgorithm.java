package com.mufeng.trade.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.mufeng.common.id.GeneSnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 交易库单库 16 分表基因法精准路由算法与动态表名配置
 * 规则：无论是传入 userId、parentOrderNo 还是 subOrderNo，其最低 4 位二进制均相同，
 * 直接通过 (id & 15) 即可 O(1) 路由至 t_parent_order_00~15 及 t_sub_order_00~15
 */
@Slf4j
@Configuration
public class GeneTablePreciseShardingAlgorithm {

    private static final ThreadLocal<String> SHARD_SUFFIX_HOLDER = new ThreadLocal<>();

    /**
     * 根据 userId 或 基因订单号(parentOrderNo / subOrderNo) 绑定当前线程的目标分表后缀 ("00" ~ "15")
     */
    public static void bindShardKey(Long userIdOrOrderNo) {
        if (userIdOrOrderNo == null) {
            throw new IllegalArgumentException("分表路由基因键 (userId 或 orderNo) 不能为空");
        }
        String suffix = GeneSnowflakeIdGenerator.extractTableSuffix(userIdOrOrderNo);
        SHARD_SUFFIX_HOLDER.set(suffix);
    }

    /**
     * 清理当前线程的分表路由上下文
     */
    public static void clearShardKey() {
        SHARD_SUFFIX_HOLDER.remove();
    }

    /**
     * 注册 MyBatis-Plus 动态表名路由拦截器（自动将 t_parent_order / t_sub_order 替换为物理分表）
     */
    @Bean
    public MybatisPlusInterceptor tradeShardingInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        DynamicTableNameInnerInterceptor dynamicTableNameInterceptor = new DynamicTableNameInnerInterceptor();

        dynamicTableNameInterceptor.setTableNameHandler((sql, tableName) -> {
            if ("t_parent_order".equalsIgnoreCase(tableName) || "t_sub_order".equalsIgnoreCase(tableName)) {
                String suffix = SHARD_SUFFIX_HOLDER.get();
                if (suffix == null) {
                    throw new IllegalStateException("执行分表 SQL 前未绑定基因路由键, tableName: " + tableName);
                }
                return tableName + "_" + suffix;
            }
            return tableName;
        });

        interceptor.addInnerInterceptor(dynamicTableNameInterceptor);
        return interceptor;
    }
}