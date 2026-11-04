package com.mufeng.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 木风-交易履约核心服务启动类 (Port: 8083)
 */
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mufeng.api")
@MapperScan("com.mufeng.trade.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.mufeng.trade", "com.mufeng.common"})
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}