package com.banner.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM 系统启动类（:8081，运维侧）。
 */
@SpringBootApplication(scanBasePackages = "com.banner")
@MapperScan("com.banner.crm.mapper")
@EnableScheduling
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
