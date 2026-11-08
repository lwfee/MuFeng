USE mufeng;

-- 1. 用户与商家基础数据
CREATE TABLE IF NOT EXISTS `t_merchant` (
  `id` bigint NOT NULL COMMENT '商家ID',
  `merchant_name` varchar(100) NOT NULL COMMENT '店铺名称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1正常 0禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

INSERT IGNORE INTO `t_merchant` (`id`, `merchant_name`) VALUES 
(201, '木风自营旗舰店'), 
(202, '星枢服饰');

INSERT IGNORE INTO `t_user` (`id`, `username`, `phone`) VALUES 
(10002, '测试买家', '13800138000'), 
(10005, '李四', '13900139000');

-- 2. 商品 SKU 表与购物车表
CREATE TABLE IF NOT EXISTS `t_product_sku` (
  `id` bigint NOT NULL COMMENT 'SKU ID',
  `merchant_id` bigint NOT NULL COMMENT '所属商家ID',
  `product_name` varchar(100) NOT NULL COMMENT '商品名称',
  `price` bigint NOT NULL COMMENT '单价(单位:分)',
  `image_url` varchar(255) DEFAULT NULL COMMENT '商品主图',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1上架 0下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

CREATE TABLE IF NOT EXISTS `t_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '购物车主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '购买数量',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

INSERT IGNORE INTO `t_product_sku` (`id`, `merchant_id`, `product_name`, `price`, `status`) VALUES 
(1001, 201, '木风自营-旗舰机械键盘 (秒杀款)', 30000, 1),
(1002, 201, '木风自营-无线鼠标 (日常款)', 10000, 1),
(2001, 202, '星枢服饰-极客连帽卫衣', 9900, 1);

-- 3. 初始化库存数据 (匹配原版 t_sku_inventory 结构)
-- 1001 为秒杀商品，日常可用库存 0，秒杀冻结库存 10
-- 1002、2001 为日常商品，可用库存 1000
INSERT IGNORE INTO `t_sku_inventory` 
(`sku_id`, `merchant_id`, `sku_name`, `sale_price`, `available_stock`, `locked_stock`, `activity_frozen_stock`) 
VALUES 
(1001, 201, '木风自营-旗舰机械键盘 (秒杀款)', 30000, 0, 0, 10),
(1002, 201, '木风自营-无线鼠标 (日常款)', 10000, 1000, 0, 0),
(2001, 202, '星枢服饰-极客连帽卫衣', 9900, 1000, 0, 0);

-- 4. 初始化支付流水表
CREATE TABLE IF NOT EXISTS `t_payment` (
  `id` bigint NOT NULL COMMENT '支付流水ID',
  `parent_order_no` bigint NOT NULL COMMENT '父订单号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `pay_amount` bigint NOT NULL COMMENT '支付金额(分)',
  `channel` varchar(20) NOT NULL COMMENT '支付渠道: ALIPAY/WECHAT',
  `trade_no` varchar(100) DEFAULT NULL COMMENT '第三方交易单号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0待支付 1已支付 2已退款',
  `pay_time` datetime DEFAULT NULL COMMENT '支付完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_order` (`parent_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

-- 5. 初始化营销与用户券数据
INSERT IGNORE INTO `t_coupon_template` 
(`id`, `coupon_name`, `threshold_amount`, `discount_amount`, `platform_subsidy_ratio`, `total_stock`, `available_stock`, `scope_type`) 
VALUES 
(888, '跨店满300减10元券', 30000, 1000, 6667, 1000, 1000, 1);

INSERT IGNORE INTO `t_user_coupon` 
(`id`, `user_id`, `template_id`, `status`, `expire_time`) 
VALUES 
(999, 10005, 888, 0, DATE_ADD(NOW(), INTERVAL 7 DAY));
