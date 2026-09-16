package com.banner.marketing.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** 营销系统通过 CRM 内部接口按 bannerId 获取定向用户，不把用户列表放进 Kafka。 */
@Component
@RequiredArgsConstructor
public class BannerAudienceClient {
    // RestClient 是 Spring Web 提供的同步 HTTP 客户端，可以理解为 RestTemplate 的现代化替代方案
    private final RestClient.Builder restClientBuilder;

    // 从配置文件读取 CRM 基础 URL，如果不存在则使用 : 后的默认地址
    @Value("${banner.crm-base-url:http://localhost:8081}")
    private String crmBaseUrl;

    // 创建 HTTP 客服端：restClientBuilder.baseUrl(crmBaseUrl).build()
    // .get() 发起 GET 请求；.retrive() 读取响应；.body(Long[].class) 将响应体转换为 Long 数组
    // TODO 分页获取
    public Set<Long> fetchUserIds(Long bannerId) {
        Long[] ids = restClientBuilder.baseUrl(crmBaseUrl).build()
                .get().uri("/internal/banner-audience/{bannerId}", bannerId)
                .retrieve().body(Long[].class);
        if (ids == null) {
            return Collections.emptySet();
        }
        return Arrays.stream(ids).collect(Collectors.toSet());
    }
}
