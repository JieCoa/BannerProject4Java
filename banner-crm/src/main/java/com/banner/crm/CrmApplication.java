package com.banner.crm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CRM 系统启动类（:8081，运维侧）。
 * 
 * @MapperScan("com.banner.crm.mapper") - MyBatis-Plus 扫 mapper
 * - 相当于告诉 MyBatis：扫描 com.banner.crm.mapper 包下面的 Mapper 接口，
 * - 并为它们创建动态代理对象。这些代理对象可以自动实现 CRUD 操作，而不需要手动编写 SQL 语句。
 * 
 * @EnableScheduling - 开启定时任务
 */
@SpringBootApplication(scanBasePackages = "com.banner")
@MapperScan("com.banner.crm.mapper")
@EnableScheduling
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
