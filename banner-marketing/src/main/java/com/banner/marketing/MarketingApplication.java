package com.banner.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 营销系统启动类（:8082，用户侧 banner 查询）。
 */
@SpringBootApplication(scanBasePackages = "com.banner")
public class MarketingApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
    }
}
