CREATE DATABASE IF NOT EXISTS `mufeng` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mufeng`;

-- ============================================================================
-- 1. 营销算价域 (Marketing Domain)
-- ============================================================================

-- 1.1 优惠券模板表
CREATE TABLE IF NOT EXISTS `t_coupon_template` (
                                                   `id` BIGINT NOT NULL COMMENT '模板ID',
                                                   `coupon_name` VARCHAR(128) NOT NULL COMMENT '优惠券名称',
    `coupon_type` TINYINT NOT NULL COMMENT '类型: 1-满减券, 2-折扣券, 3-直降津贴',
    `scope_type` TINYINT NOT NULL COMMENT '适用范围: 1-全平台跨店, 2-指定店铺, 3-指定SKU',
    `merchant_id` BIGINT DEFAULT 0 COMMENT '所属商家ID (0表示平台发券)',
    `threshold_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '使用门槛金额(单位:分)',
    `discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '立减面额(单位:分)或折扣率(如8500表示85折)',
    `platform_subsidy_ratio` INT NOT NULL DEFAULT 10000 COMMENT '平台出资万分比(如6667表示平台承担66.67%, 商家承担33.33%)',
    `stack_rule_bitmap` BIGINT NOT NULL DEFAULT 0 COMMENT '叠加规则位图(BitMask控制不同类型券互斥)',
    `total_stock` INT NOT NULL COMMENT '发券总数量',
    `available_stock` INT NOT NULL COMMENT '剩余可领数量',
    `limit_per_user` INT NOT NULL DEFAULT 1 COMMENT '每人限领张数',
    `valid_start_time` DATETIME NOT NULL COMMENT '有效期开始时间',
    `valid_end_time` DATETIME NOT NULL COMMENT '有效期结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-生效中, 2-已停用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status_time` (`status`, `valid_start_time`, `valid_end_time`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- 1.2 用户优惠券表
CREATE TABLE IF NOT EXISTS `t_user_coupon` (
                                               `id` BIGINT NOT NULL COMMENT '用户券ID',
                                               `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                               `template_id` BIGINT NOT NULL COMMENT '券模板ID',
                                               `locked_parent_order_no` BIGINT DEFAULT NULL COMMENT '锁定该券的父订单号',
                                               `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-未使用, 2-下单已锁定, 3-已核销消费, 4-已过期',
                                               `receive_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
                                               `use_time` DATETIME DEFAULT NULL COMMENT '使用/锁定时间',
                                               `expire_time` DATETIME NOT NULL COMMENT '过期时间',
                                               `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                                               PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_order_no` (`locked_parent_order_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券实例表';

-- ============================================================================
-- 2. 双模库存域 (Inventory Domain)
-- ============================================================================

-- 2.1 商品双模库存表 (动静分离：普通库存走DB锁，秒杀库存划拨至Redis)
CREATE TABLE IF NOT EXISTS `t_sku_inventory` (
                                                 `sku_id` BIGINT NOT NULL COMMENT '商品SKU ID',
                                                 `merchant_id` BIGINT NOT NULL COMMENT '所属商家ID',
                                                 `sku_name` VARCHAR(128) NOT NULL COMMENT '商品名称',
    `sale_price` BIGINT NOT NULL COMMENT '销售单价(单位:分)',
    `available_stock` INT NOT NULL DEFAULT 0 COMMENT '日常可用库存(普通下单直接扣减此字段)',
    `locked_stock` INT NOT NULL DEFAULT 0 COMMENT '日常下单锁定中库存(支付成功后实扣, 关单则回滚)',
    `activity_frozen_stock` INT NOT NULL DEFAULT 0 COMMENT '秒杀活动划拨冻结库存(独立划拨进Redis, 与日常库存物理隔离)',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`sku_id`),
    KEY `idx_merchant` (`merchant_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品双模库存表';

-- 2.2 库存扣减与释放幂等流水表
CREATE TABLE IF NOT EXISTS `t_inventory_deduct_log` (
                                                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                        `parent_order_no` BIGINT NOT NULL COMMENT '父订单号',
                                                        `sub_order_no` BIGINT NOT NULL COMMENT '子订单号',
                                                        `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
                                                        `deduct_mode` TINYINT NOT NULL COMMENT '扣减模式: 1-普通DB行锁库存, 2-秒杀Redis划拨库存',
                                                        `deduct_num` INT NOT NULL COMMENT '扣减数量',
                                                        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-已预扣锁定, 2-已实扣确认, 3-已回滚释放',
                                                        `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                        PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_sku` (`sub_order_no`, `sku_id`),
    KEY `idx_parent_order` (`parent_order_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存扣减幂等流水表';

-- ============================================================================
-- 3. 交易履约域 (Trade Domain - 单库16分表 & 营销出资分摊快照)
-- ============================================================================

-- 3.1 订单项营销出资分摊快照表 (正向最大余数法算价结果落盘，支撑部分退货与商家结算)
CREATE TABLE IF NOT EXISTS `t_order_item_promo_snapshot` (
                                                             `id` BIGINT NOT NULL COMMENT '快照ID',
                                                             `parent_order_no` BIGINT NOT NULL COMMENT '父订单号',
                                                             `sub_order_no` BIGINT NOT NULL COMMENT '所属店铺子订单号',
                                                             `user_id` BIGINT NOT NULL COMMENT '买家用户ID',
                                                             `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
                                                             `sku_id` BIGINT NOT NULL COMMENT '商品SKU ID',
                                                             `buy_quantity` INT NOT NULL COMMENT '购买数量',
                                                             `origin_total_amount` BIGINT NOT NULL COMMENT '商品原总价(单位:分)',
                                                             `total_discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '分摊总优惠金额(单位:分)',
                                                             `platform_subsidy_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '其中：平台补贴出资金额(单位:分, 结算时需补给商家)',
                                                             `merchant_discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '其中：商家自主让利金额(单位:分)',
                                                             `actual_pay_amount` BIGINT NOT NULL COMMENT '买家分摊实付金额(单位:分, 退货时按此金额原路退回)',
                                                             `refund_status` TINYINT NOT NULL DEFAULT 0 COMMENT '售后状态: 0-无售后, 1-退款中, 2-已全额退款',
                                                             `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                             PRIMARY KEY (`id`),
    KEY `idx_sub_order` (`sub_order_no`),
    KEY `idx_parent_order` (`parent_order_no`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单项营销与出资方分摊快照表';

-- 3.2 自动创建 16 张父订单分表 (t_parent_order_00 ~ 15) 与 16 张子订单分表 (t_sub_order_00 ~ 15)
DROP PROCEDURE IF EXISTS `init_sharded_trade_tables`;
DELIMITER $$
CREATE PROCEDURE `init_sharded_trade_tables`()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE suffix VARCHAR(4);
    WHILE i < 16 DO
        SET suffix = LPAD(i, 2, '0');

        -- 创建父订单分表 (用于合并收银台支付与全局优惠券绑定)
        SET @parent_sql = CONCAT('CREATE TABLE IF NOT EXISTS `t_parent_order_', suffix, '` (
            `parent_order_no` BIGINT NOT NULL COMMENT ''父订单号(末4位二进制含user_id基因)'',
            `user_id` BIGINT NOT NULL COMMENT ''买家用户ID(分片键)'',
            `total_origin_amount` BIGINT NOT NULL COMMENT ''订单商品原总价(分)'',
            `total_discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''总优惠金额(分)'',
            `total_pay_amount` BIGINT NOT NULL COMMENT ''合并应支付总金额(分)'',
            `used_coupon_id` BIGINT DEFAULT NULL COMMENT ''使用的用户优惠券ID'',
            `order_status` TINYINT NOT NULL DEFAULT 10 COMMENT ''状态: 10-待支付, 20-已支付(已激活子单), 30-超时已关闭, 40-已全部取消'',
            `pay_channel` TINYINT DEFAULT NULL COMMENT ''支付渠道: 1-微信支付, 2-支付宝'',
            `channel_trade_no` VARCHAR(64) DEFAULT NULL COMMENT ''第三方支付渠道流水号'',
            `pay_time` DATETIME DEFAULT NULL COMMENT ''支付成功时间'',
            `expire_time` DATETIME NOT NULL COMMENT ''支付超时关单时间(默认15分钟)'',
            `version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'',
            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`parent_order_no`),
            KEY `idx_user_status` (`user_id`, `order_status`),
            KEY `idx_expire_time` (`order_status`, `expire_time`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''父订单分表_', suffix, ''';');
PREPARE stmt1 FROM @parent_sql;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- 创建店铺子订单分表 (按商家拆单，独立发货、独立确认收货、独立部分退款与清分)
SET @sub_sql = CONCAT('CREATE TABLE IF NOT EXISTS `t_sub_order_', suffix, '` (
            `sub_order_no` BIGINT NOT NULL COMMENT ''店铺子订单号(末4位二进制含user_id基因)'',
            `parent_order_no` BIGINT NOT NULL COMMENT ''关联父订单号'',
            `user_id` BIGINT NOT NULL COMMENT ''买家用户ID(分片键)'',
            `merchant_id` BIGINT NOT NULL COMMENT ''店铺商家ID'',
            `sub_origin_amount` BIGINT NOT NULL COMMENT ''子单原总价(分)'',
            `sub_discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''子单分摊总优惠(分)'',
            `platform_subsidy_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''分摊平台补贴金额(分)'',
            `merchant_discount_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''分摊商家让利金额(分)'',
            `sub_pay_amount` BIGINT NOT NULL COMMENT ''子单买家实付金额(分)'',
            `refunded_amount` BIGINT NOT NULL DEFAULT 0 COMMENT ''累计已退款金额(分)'',
            `order_status` TINYINT NOT NULL DEFAULT 10 COMMENT ''状态机: 10-待支付, 20-待发货, 30-已发货待收货, 40-已确认收货, 50-已完成(过售后期可结算), 60-交易关闭'',
            `settle_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''清结算状态: 0-未生成结算单, 1-已生成待结算, 2-已打款完成'',
            `deliver_time` DATETIME DEFAULT NULL COMMENT ''发货时间'',
            `confirm_time` DATETIME DEFAULT NULL COMMENT ''确认收货时间'',
            `version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'',
            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (`sub_order_no`),
            KEY `idx_parent_order` (`parent_order_no`),
            KEY `idx_user_created` (`user_id`, `created_at`),
            KEY `idx_merchant_status` (`merchant_id`, `order_status`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''店铺子订单分表_', suffix, ''';');
PREPARE stmt2 FROM @sub_sql;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET i = i + 1;
END WHILE;
END$$
DELIMITER ;

CALL `init_sharded_trade_tables`();
DROP PROCEDURE IF EXISTS `init_sharded_trade_tables`;

-- ============================================================================
-- 4. 清结算与对账域 (Settlement & Reconciliation Domain)
-- ============================================================================

-- 4.1 商家清分结算账单表 (公式：商家应结 = 买家实付 + 平台补贴 - 类目佣金)
CREATE TABLE IF NOT EXISTS `t_merchant_settlement_bill` (
                                                            `id` BIGINT NOT NULL COMMENT '结算单ID',
                                                            `sub_order_no` BIGINT NOT NULL COMMENT '关联子订单号',
                                                            `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
                                                            `user_pay_amount` BIGINT NOT NULL COMMENT '买家实付净额(已扣除逆向退款, 单位:分)',
                                                            `platform_subsidy_amount` BIGINT NOT NULL COMMENT '平台补贴补回金额(单位:分)',
                                                            `commission_rate` INT NOT NULL DEFAULT 500 COMMENT '类目佣金万分比(如500代表5%)',
                                                            `platform_commission_amount` BIGINT NOT NULL COMMENT '平台抽佣金额(单位:分)',
                                                            `merchant_net_settle_amount` BIGINT NOT NULL COMMENT '商家最终应结净额(单位:分)',
                                                            `bill_status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-待对账核验, 2-对账无误待打款, 3-已打款, 4-对账异常冻结',
                                                            `settle_date` DATE NOT NULL COMMENT '应结算账期日',
                                                            `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                            `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                            PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sub_order` (`sub_order_no`),
    KEY `idx_merchant_date` (`merchant_id`, `settle_date`, `bill_status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家清分结算明细单';

-- 4.2 T+1 渠道对账差错与日切跨日缓冲表
CREATE TABLE IF NOT EXISTS `t_reconciliation_diff` (
                                                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                                       `reconcile_date` DATE NOT NULL COMMENT '对账批次日期',
                                                       `parent_order_no` BIGINT NOT NULL COMMENT '平台父订单号',
                                                       `channel_trade_no` VARCHAR(64) DEFAULT NULL COMMENT '支付渠道流水号',
    `diff_type` TINYINT NOT NULL COMMENT '差错类型: 0-日切跨天缓冲挂起(23:55-00:05), 1-长款(渠道已付平台未付), 2-短款(平台已付渠道无记录), 3-金额不一致',
    `platform_amount` BIGINT DEFAULT 0 COMMENT '平台订单记录金额(分)',
    `channel_amount` BIGINT DEFAULT 0 COMMENT '支付渠道账单金额(分)',
    `handle_status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态: 0-待次日自动复核(跨日单), 1-待人工/补单处理, 2-已平账解决',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '差错说明或处理结果',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_date` (`parent_order_no`, `reconcile_date`),
    KEY `idx_status_type` (`handle_status`, `diff_type`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='T+1渠道对账差错与跨日缓冲表';