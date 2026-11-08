package com.mufeng.trade.adapter.web;

import com.mufeng.trade.infrastructure.persistence.po.ProductSkuPO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/trade/product")
public class ProductController {

    @GetMapping("/list")
    public List<ProductSkuPO> getProductList() {
        // 直接硬编码返回，不走 ShardingSphere
        List<ProductSkuPO> list = new ArrayList<>();
        
        ProductSkuPO p1 = new ProductSkuPO();
        p1.setId(1001L);
        p1.setMerchantId(201L);
        p1.setProductName("木风自营-旗舰机械键盘 (秒杀款)");
        p1.setPrice(30000L);
        p1.setStatus(1);
        list.add(p1);

        ProductSkuPO p2 = new ProductSkuPO();
        p2.setId(1002L);
        p2.setMerchantId(201L);
        p2.setProductName("木风自营-无线鼠标 (日常款)");
        p2.setPrice(10000L);
        p2.setStatus(1);
        list.add(p2);

        ProductSkuPO p3 = new ProductSkuPO();
        p3.setId(2001L);
        p3.setMerchantId(202L);
        p3.setProductName("星枢服饰-极客连帽卫衣");
        p3.setPrice(9900L);
        p3.setStatus(1);
        list.add(p3);

        return list;
    }
}