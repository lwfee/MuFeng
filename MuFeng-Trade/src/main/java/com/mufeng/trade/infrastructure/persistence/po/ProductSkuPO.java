package com.mufeng.trade.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product_sku")
public class ProductSkuPO {
    @TableId
    private Long id;
    private Long merchantId;
    private String productName;
    private Long price;
    private String imageUrl;
    private Integer status;
}