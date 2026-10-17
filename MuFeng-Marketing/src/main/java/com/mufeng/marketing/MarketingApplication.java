package com.mufeng.marketing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 木风-营销算价服务启动类 (Port: 8081)
 */
@EnableDiscoveryClient
@MapperScan("com.mufeng.marketing.infrastructure.persistence.mapper")
@SpringBootApplication(scanBasePackages = {"com.mufeng.marketing", "com.mufeng.common"})
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}