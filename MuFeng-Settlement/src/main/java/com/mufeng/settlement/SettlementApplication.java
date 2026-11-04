package com.mufeng.settlement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 木风-清结算与对账服务启动类 (Port: 8084)
 */
@EnableDiscoveryClient
@MapperScan("com.mufeng.settlement.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.mufeng.settlement", "com.mufeng.common"})
public class SettlementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementApplication.class, args);
    }
}