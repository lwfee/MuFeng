package com.mufeng.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 木风-双模库存服务启动类 (Port: 8082)
 */
@EnableDiscoveryClient
@MapperScan("com.mufeng.inventory.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.mufeng.inventory", "com.mufeng.common"})
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}