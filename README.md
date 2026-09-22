# BannerProject4Java

抖音电商场景的 Banner 配置与投放示例项目。它把运营配置和用户查询拆成两个独立服务：CRM 负责可靠写入，Marketing 负责消费变更并提供“此刻对当前用户可见”的 Banner。

> 本项目只覆盖 Banner 业务，不包含登录、商品、订单和前端页面。`jumpUrl` 由调用方在用户点击 Banner 时跳转到直播间、活动页或商品详情页。

## 1. 解决什么问题

项目演示一条可运行的 Banner 数据链路：

```text
运营配置
   │ HTTP CRUD + 可选 userIds
   ▼
CRM (:8081)
   │ MySQL 事务：主数据 + 人群分片 + Outbox
   │ Kafka banner-topic（纯 JSON，key=bannerId）
   ▼
Marketing (:8082)
   │ Consumer 按 version 防乱序
   │ Redis 三类 Key + Caffeine localCache
   ▼
用户查询
GET /marketing/banners?bizCode=...&userId=...
```

核心业务规则：

- 没有 `userIds` 的 Banner 对所有用户可见；
- 有 `userIds` 的 Banner 只对名单中的用户可见；
- 查询同时检查业务线、当前时间、人群包和 `sort`；
- CRM 更新、删除必须携带当前 `version`，并发冲突时拒绝覆盖；
- MySQL 删除是物理删除，但 Redis 保留删除版本，避免迟到的旧消息让 Banner 复活。

## 2. 模块与职责

| 模块 | 端口 | 职责 |
| --- | ---: | --- |
| `banner-common` | — | 共享实体、消息体、操作枚举和 Redis Key 规则 |
| `banner-crm` | 8081 | Banner/业务线数据、用户名单分片、乐观锁、Outbox、Kafka 投递与补偿 |
| `banner-marketing` | 8082 | Kafka 消费、Redis 写入、名单回源、localCache 和用户可见性查询 |

基础设施由 `docker-compose.yml` 提供：

| 服务 | 地址 | 用途 |
| --- | --- | --- |
| MySQL 8 | `localhost:3307` | CRM 主数据、名单分片、Outbox |
| Redis 7 | `localhost:6379` | Marketing 查询缓存 |
| Kafka 3.9 KRaft | `localhost:9092` | CRM 到 Marketing 的变更事件 |

## 3. 一次变更如何到达用户侧

1. CRM 校验业务线和时间范围，在 MySQL 事务中写入 `banner_info`。
2. `userIds` 按每 1000 个拆入 `banner_user_shard`；空名单表示全量可见。
3. 主数据与 `banner_change_outbox` 同事务提交，避免数据库成功但事件丢失。
4. Outbox 任务投递 Kafka；Kafka 消息不携带完整用户名单，只携带 `buckets`。
5. Marketing 按 `bannerId` 比较 Redis 中的版本，丢弃旧消息；非删除事件再回源 CRM 获取名单并写入桶。
6. 用户查询优先从 Caffeine 读取业务日期 Map，未命中时读取 Redis，然后按时间、人群和排序规则过滤。

## 4. 数据与缓存设计

### MySQL 表

- `business_info`：业务线，初始化包含 `agri`、`digital`、`clothes`、`beauty`。
- `banner_info`：Banner 主数据；`version` 是数据库乐观锁版本。
- `banner_user_shard`：名单分片，每行最多 1000 个 userId。
- `banner_change_outbox`：可靠投递事件，记录 `CREATE`、`UPDATE`、`DELETE`、重试次数和状态。

`BannerInfo` 请求字段：

| 字段 | 说明 |
| --- | --- |
| `bizId` | 业务线 ID，例如 `1=agri`、`2=digital` |
| `title` / `imageUrl` / `jumpUrl` | 展示信息和点击跳转地址 |
| `startTime` / `endTime` | ISO-8601 的本地时间，`endTime` 必须晚于 `startTime` |
| `sort` | 升序展示，数值越小越靠前 |
| `userIds` | CRM 请求字段，可省略或传数组；不进入 Kafka |
| `id` / `version` | 更新、删除时使用；以 CRM 最新返回值为准 |

### Redis 三 Key

| Key | 类型 | 用途 |
| --- | --- | --- |
| `banner:{id}` | String | 保存完整消息、`version`、`deleted`、`buckets`，用于乱序判断 |
| `banner:{bizCode}:{yyyyMMdd}` | Hash | 保存某业务线某日期的 `bannerId -> BannerMessage` 查询 Map |
| `banner:{id}:bucketIndex:{n}` | Set | 保存第 `n` 个人群桶，每桶最多 1000 个 userId |

三个 Key 的过期时间都以 Banner `endTime` 所在日期的次日零点为准。`buckets=0` 表示全量可见；桶数量减少时超范围旧桶不立即删除，查询不会扫描它们，等待 TTL 自然清理。

## 5. 快速开始

以下步骤针对 Windows PowerShell。请先确认 Docker Desktop 已启动，并使用 JDK 17；PowerShell 中请求命令使用 `curl.exe`，不要使用别名 `curl`。

### 5.1 启动 MySQL、Redis、Kafka

在项目根目录执行：

```powershell
docker compose up -d
docker compose ps
```

确认 `banner-mysql`、`banner-redis`、`banner-kafka` 都处于运行状态。MySQL 数据卷第一次创建时会自动执行 `banner-crm/src/main/resources/db/schema.sql`，其中会初始化四条业务线。

如果需要完全重置本地数据：

```powershell
docker compose down -v
docker compose up -d
docker compose ps
```

### 5.2 使用 JDK 17 编译

下面的路径是示例路径；如果本机 JDK 17 安装位置不同，请替换 `JAVA_HOME`：

```powershell
$env:JAVA_HOME="D:\develop_tools\jdk\jdk17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn clean install -DskipTests
```

构建成功的判断标准是 Maven 输出 `BUILD SUCCESS`。

### 5.3 启动两个 Spring Boot 服务

保持两个 PowerShell 窗口。项目配置已经把 CRM、Marketing 及中间件地址分别固定为 `8081`、`8082`、`3307`、`6379`、`9092`。

窗口一：

```powershell
$env:JAVA_HOME="D:\develop_tools\jdk\jdk17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run -pl banner-crm
```

窗口二：

```powershell
$env:JAVA_HOME="D:\develop_tools\jdk\jdk17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run -pl banner-marketing
```

启动日志没有连接异常后，再进行下一步。Marketing 的 Kafka consumer 使用 `earliest`，因此新建 consumer group 时可以消费已有事件。

### 5.4 创建一个带定向名单的 Banner

下面的示例创建 `digital` 业务线 Banner，只对 `10001`、`10002` 可见。时间窗口使用当前示例环境的日期；如果运行日期已不在窗口内，请把两个时间改成未来的有效区间。

```powershell
$body = @{
    bizId = 2
    title = "手机数码大促"
    imageUrl = "https://cdn.example.com/banner/digital.png"
    jumpUrl = "https://activity.example.com/digital"
    startTime = "2026-09-20T00:00:00"
    endTime = "2026-09-30T23:59:59"
    sort = 1
    userIds = @(10001, 10002)
} | ConvertTo-Json -Compress

$result = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/crm/banner" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))

$result | ConvertTo-Json
$bannerId = $result.id
$bannerVersion = $result.version
```

响应中重点关注：

- `id`：后续检查 Redis 和更新/删除时使用；
- `version`：新建通常为 `0`，更新或删除必须使用最新版本；
- `buckets`：两个人时为 `1`；空名单时为 `0`。

### 5.5 验证“写入 → Kafka → Redis → 用户查询”

等待几秒让 Outbox 投递和 Consumer 处理完成，然后执行：

```powershell
# 10001 在名单中，应返回该 Banner
curl.exe "http://localhost:8082/marketing/banners?bizCode=digital&userId=10001"

# 20001 不在名单中，应返回 []
curl.exe "http://localhost:8082/marketing/banners?bizCode=digital&userId=20001"

# 不传 userId 时，定向 Banner 不应返回；只会返回 buckets=0 的 Banner
curl.exe "http://localhost:8082/marketing/banners?bizCode=digital"
```

成功时，第一条响应应包含 `title`、`imageUrl`、`jumpUrl`、`sort` 等展示字段；第二、第三条在没有其他全量 Banner 时应为 `[]`。Marketing 返回的是用户侧 VO，不包含 CRM 内部名单。

### 5.6 检查 Redis 三类 Key

先查看业务日期。示例的 `2026-09-20` 对应 Redis 日期 `20260920`：

```powershell
# 单 Banner 状态：确认 version、deleted、buckets
docker exec banner-redis redis-cli GET "banner:$bannerId"
docker exec banner-redis redis-cli TTL "banner:$bannerId"

# 业务日期 Hash：确认 Banner 已进入 digital 查询入口
docker exec banner-redis redis-cli HGETALL "banner:digital:20260920"
docker exec banner-redis redis-cli TTL "banner:digital:20260920"

# 人群桶：应能看到 10001、10002
docker exec banner-redis redis-cli SMEMBERS "banner:${bannerId}:bucketIndex:0"
docker exec banner-redis redis-cli TTL "banner:${bannerId}:bucketIndex:0"
```

> PowerShell 使用 `${bannerId}` 明确变量边界。也可以直接把最后两条命令中的变量替换为响应里的实际数字，例如 `banner:5:bucketIndex:0`。

### 5.7 验证更新与删除的版本控制

先把标题改掉。请求体必须带 `id` 和当前 `version`；局部更新未传的字段由服务保留原值。

```powershell
$updateBody = @{
    id = $bannerId
    version = $bannerVersion
    title = "手机数码大促（已更新）"
    sort = 2
} | ConvertTo-Json -Compress

$updated = Invoke-RestMethod `
    -Method Put `
    -Uri "http://localhost:8081/crm/banner" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($updateBody))

$updated | ConvertTo-Json
$bannerVersion = $updated.version
```

再删除：

```powershell
curl.exe -X DELETE "http://localhost:8081/crm/banner/${bannerId}?version=$bannerVersion"
```

删除成功后，Marketing 的业务日期 Hash 会移除该 Banner；单 Banner Key 会短暂保留 `deleted=true` 和删除版本，用于拒绝迟到旧消息。

## 6. 接口清单

### CRM（`http://localhost:8081`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/crm/banner` | 新增 Banner，可传 `userIds` |
| `PUT` | `/crm/banner` | 更新 Banner，必须传 `id`、`version`；传 `userIds` 时替换整个人群包 |
| `DELETE` | `/crm/banner/{id}?version={version}` | 按当前版本物理删除 Banner |
| `GET` | `/crm/banner/list?bizId={bizId}` | 查询 CRM Banner 列表，`bizId` 可省略 |
| `GET` | `/internal/banner-audience/{bannerId}` | Marketing 回源获取名单 |

### Marketing（`http://localhost:8082`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/marketing/banners?bizCode=digital&userId=10001` | 查询当前用户可见 Banner；`userId` 可省略 |

## 7. 可靠性与已知限制

已实现：

- 数据库乐观锁：更新、删除通过版本条件避免并发覆盖；
- Outbox：业务写入和事件记录在同一事务，失败事件由任务重试；
- 版本防乱序：Marketing 以 `banner:{id}` 的版本判断旧消息；
- 删除防复活：物理删除后 Redis 保留删除状态；
- 大名单拆分：MySQL 和 Redis 都按 1000 条分片，Kafka 不传 userIds。

当前限制：

- `messageId` 的显式 Redis SETNX 去重仍是 TODO，目前依赖版本比较保证重复消费结果稳定；
- localCache TTL 默认 30 秒，多实例场景允许短暂脏读；
- Redis 人群桶更新不是单个 Lua/事务操作，更新期间可能短暂读到中间状态；
- CRM 回源失败会延迟定向名单同步，后续应增加超时、重试和监控；
- `schema.sql` 只在 MySQL 首次创建数据卷时自动执行，结构变更需手动迁移或重建数据卷。

## 8. 开发与验证建议

```powershell
# 只编译，不运行服务
mvn clean install -DskipTests

# 查看中间件日志
docker compose logs -f mysql redis kafka

# 停止中间件但保留数据
docker compose down
```

建议按以下顺序补充自动化验证：空名单全量可见、指定用户命中/未命中、1000 条边界分片、名单更新后的桶范围、并发版本冲突、DELETE 晚于旧 UPDATE、Outbox 失败重试。
