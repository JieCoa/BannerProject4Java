package com.banner.crm.controller;

import com.banner.common.entity.BannerInfo;
import com.banner.crm.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * banner 管理接口（运维人员使用）
 * 
 * @RestController 声明：BannerController 是一个 Spring MVC 控制器，并且
 * 其中方法返回的对象默认会转换成 JSON。实际上相当于两个注解的组合：
 *  - @Controller 表示这是一个 Web 控制器，Spring 会发现并管理它。
 *  - @ResponseBody 表示方法返回值不用于查找 HTML 页面，而是直接写入 HTTP 响应体。
 * 
 * @RequestMapping 用来定义 URL 路径映射。写在类上时，表示这个类中所有接口的
 * 公共路径前缀是：/crm/banner。
 * - 类级别的 @RequestMapping 可以避免每个方法重复写 /crm/banner。
 * 
 * @RequiredArgsConstructor 是 Lombok 提供的注解。会根据 final 字段自动生成
 * 构造方法，相当于自动生成：
 *   public BannerController(BannerService bannerService) {
 *       this.bannerService = bannerService;
 *   }
 * - Spring 会通过这个构造方法自动注入 BannerService，这叫构造器注入。
 * 
 * @PostMapping 表示：这个方法处理 HTTP POST 请求。通常 POST 用于新增资源。
 * 
 * @RequestBody 表示：把 HTTP 请求体中的 JSON 内容转换成 Java 对象。
 * 
 * @PathVariable 表示：从 URL 路径中取出变量，赋给方法参数。由于方法参数名
 * 也是 id，所以可以省略 "id"。
 * 
 * @RequestParam 用来接收 URL 查询参数。required = false 表示这个参数不是必填的。
 * - 例如请求：GET /crm/banner/list?bizId=2
 */
@RestController
@RequestMapping("/crm/banner")
@RequiredArgsConstructor
public class BannerController {

    /**
     * 因为控制器创建后，bannerService 不应该再被替换。
     * final 可以保证它只赋值一次，也能帮助 Lombok生成构造方法。
     */
    private final BannerService bannerService;

    @PostMapping
    public BannerInfo create(@RequestBody BannerInfo banner) {
        return bannerService.create(banner);
    }

    @PutMapping
    public BannerInfo update(@RequestBody BannerInfo banner) {
        return bannerService.update(banner);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable Long id, @RequestParam Long version) {
        bannerService.delete(id, version);
        return "ok";
    }

    @GetMapping("/list")
    public List<BannerInfo> list(@RequestParam(required = false) Long bizId) {
        return bannerService.list(bizId);
    }
}
