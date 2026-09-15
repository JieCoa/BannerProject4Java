# BannerProject4Java

抖音电商平台 banner 模块：商家在指定时间段投放活动宣传图，用户在主页 banner 位看到当前生效的宣传图，点击后跳转到对应链接（直播间 / 活动页 / 商品详情页）。

## 系统架构

```
                          ┌─────────────────────────┐
      运维人员  ──CRUD──▶  │  banner-crm  (:8081)    │──写──▶ MySQL
                          │  (CRM 系统)             │──发消息──▶ ┐
                          └─────────────────────────┘            │
                             定时补偿任务(每5分钟全量重发)          ▼
                                                              Kafka: banner-topic
                                                                   │
                          ┌─────────────────────────┐            ▼
      用户  ◀──banner列表── │  banner-marketing(:8082)│◀─消费─ 按version比对
                          │  (营销系统)             │──写──▶ Redis Hash
                          └─────────────────────────┘            ▲
                                     │ localCache miss           │
                                     └──────── Caffeine 本地缓存 TTL 30s
```



## 模块说明


| 模块                 | 说明                                                           |
| ------------------ | ------------------------------------------------------------ |
| `banner-common`    | 共享实体（BusinessInfo/BannerInfo）、Kafka 消息体（BannerMessage）、枚举、常量 |
| `banner-crm`       | CRM 系统（:8081）：运维对 banner/业务线增删改查，写 MySQL，事务提交后发 Kafka        |
| `banner-marketing` | 营销系统（:8082）：消费 Kafka 写 Redis，提供用户侧查询（localCache -> Redis）    |




## Redis 键值设计

业务含义：**"用户在某一天能看到的 banner 集合"**


| Key                           | 类型     | Value                             | 说明                                                   |
| ----------------------------- | ------ | --------------------------------- | ---------------------------------------------------- |
| `banner:{bizCode}:{yyyyMMdd}` | Hash   | field=bannerId, value=banner JSON | 业务线 + 有效时间（按天切分），banner 跨几天就写几个 key，key 过期时间为当天 24 点 |
| `banner:data:{bannerId}`      | String | 最新一条消息 JSON                       | 乱序比较 + 旧覆盖天数回滚的依据                                    |
| `banner:tomb:{bannerId}`      | String | version（TTL 24h）                  | 删除墓碑，防止乱序旧消息复活已删 banner                              |




## 消息可靠性设计（三个核心问题的解法）

1. **消息丢失**：CRM 定时补偿任务每 5 分钟把所有"未过期" banner 全量重发到 Kafka，消费端按 version 幂等覆盖，最终一致。
2. **insert/update 乱序**（如运维先 insert 后马上 update，但 update 消息先到）：
  - CRM 以 `bannerId` 为 Kafka 消息 key → 同一 banner 的消息在同一分区内有序；
  - 消息携带 `version`（毫秒时间戳），消费端只接受 version 更大的消息，迟到的旧消息直接丢弃；
  - DELETE 写墓碑，防止乱序消息复活已删数据；定时补偿任务兜底自愈。
3. **消息幂等**：TODO（消息体已预留 `messageId`，可基于 Redis SETNX 按 messageId 去重；当前靠 version 比对保证重复消费结果不变）。



## 快速开始



### 0. 环境要求

- JDK 17+、Maven 3.8+
- Docker（用于一键启动 MySQL/Redis/Kafka；如果本机已有这三件中间件，可跳过并自行改 `application.yml` 中的连接配置）



### 1. 启动中间件

```powershell
docker compose up -d
```

MySQL 首次启动会自动执行 `banner-crm/src/main/resources/db/schema.sql`（建库建表 + 示例数据）。

```powershell
# 只清理容器和网络，卷中的数据（如数据库数据、上传文件等）保留在宿主机上。
docker compose down

# 在上述基础上，额外删除 compose 文件中 volumes: 声明的命名卷和容器的匿名卷。卷内数据不可恢复。
docker compose down -v
```



### 2. 构建并启动两个系统

```powershell
mvn clean install -DskipTests
mvn spring-boot:run -pl banner-crm        # 窗口 1：CRM 系统 :8081
mvn spring-boot:run -pl banner-marketing  # 窗口 2：营销系统 :8082
```



### 3. 验证

```powershell
# 运维：给"助农产品"创建一个 banner（当前时间起 3 天有效）
# powershell
$body = @{
    bizId = 2
    title = "双11预热活动"
    imageUrl = "https://cdn.example.com/banner/double11.png"
    jumpUrl = "https://live.douyin.com/99999"
    startTime = "2026-09-15T00:00:00"
    endTime = "2026-11-11T23:59:59"
    sort = 1
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/crm/banner" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

# 用户侧：查询"助农产品"此刻可见的 banner
curl.exe "http://localhost:8082/marketing/banners?bizCode=agri"

curl.exe "http://localhost:8082/marketing/banners?bizCode=digital"
```

```cmd
# cmd
curl -X POST "http://localhost:8081/crm/banner" -H "Content-Type: application/json" -d "{\"bizId\":1,\"title\":\"助农水果节\",\"imageUrl\":\"https://cdn.example.com/banner/fruit.png\",\"jumpUrl\":\"https://live.douyin.com/99999\",\"startTime\":\"2026-09-10T00:00:00\",\"endTime\":\"2026-09-17T23:59:59\",\"sort\":1}"
```



## 接口清单

CRM（运维侧，:8081）：


| 方法     | 路径                        | 说明                |
| ------ | ------------------------- | ----------------- |
| POST   | `/crm/banner`             | 新增 banner         |
| PUT    | `/crm/banner`             | 更新 banner（发全量消息）  |
| DELETE | `/crm/banner/{id}`        | 删除 banner         |
| GET    | `/crm/banner/list?bizId=` | banner 列表（可按业务过滤） |
| POST   | `/crm/business`           | 新增业务线             |
| GET    | `/crm/business/list`      | 业务线列表             |


营销（用户侧，:8082）：


| 方法  | 路径                                | 说明                                      |
| --- | --------------------------------- | --------------------------------------- |
| GET | `/marketing/banners?bizCode=agri` | 某业务线"此刻"可见 banner（含 jumpUrl，已按 sort 排序） |


