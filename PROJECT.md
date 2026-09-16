# PROJECT.md — 项目状态看板

> **维护规则**：每次任务开始先读本文件；完成工作后只更新发生变化的内容。
> 发现遗漏任务补充到「待办」，重复信息就地合并，「下一步」必须始终可直接执行。
> 最后更新：2026-09-15

## 1. 项目目标

抖音电商平台 banner 模块（只做 banner 业务，不含登录 / 商品 / 订单 / 前端）：

- **B 端 · CRM 系统（:8081）**：运维对业务线、banner 增删改查，写 MySQL，事务提交后发 Kafka 变更消息
- **同步链路**：Kafka（`banner-topic`）传递变更消息，营销系统消费后写入 Redis
- **C 端 · 营销系统（:8082）**：用户查询"此刻可见"的 banner 列表（localCache → Redis），点击后按 jumpUrl 跳转（直播间 / 活动页 / 商品详情页）

## 2. 当前状态

**代码全部完成，编译通过（2026-09-10），尚未做运行时联调。**

| 事项 | 状态 |
| --- | --- |
| banner-common（实体 / 消息体 / 枚举 / 常量） | ✅ 完成 |
| banner-crm（CRUD + 发消息 + 补偿任务，:8081） | ✅ 完成 |
| banner-marketing（消费 + Redis + localCache 查询，:8082） | ✅ 完成 |
| docker-compose.yml / schema.sql（含示例数据）/ README.md | ✅ 完成 |
| `mvn clean install`（JDK 17） | ✅ BUILD SUCCESS，无 lint 错误 |
| 运行时联调（中间件 + 双服务 + 全链路验证） | ⬜ 未开始 |

## 3. 关键决策

| # | 决策 | 理由 |
| --- | --- | --- |
| D1 | Spring Boot 3.5.16 + JDK 17，Maven 单仓库多模块 | 已确认；双系统共享实体与消息体最简单 |
| D2 | Redis KV：key=`banner:{bizCode}:{yyyyMMdd}`，Hash（field=bannerId） | 对应"用户某天可见的 banner 集合"；banner 跨几天写几个 key，key 当天 24 点过期 |
| D3 | 辅助 key：`banner:data:{id}`（最新消息 JSON）、`banner:tomb:{id}`（墓碑，TTL 24h） | 乱序版本比较、旧覆盖天数回滚、防止迟到消息复活已删 banner |
| D4 | Kafka 消息 key=bannerId + 消费端 version 比对 + 墓碑 + 定时补偿（5 分钟全量重发） | 解决 insert/update 乱序（需求 6）与消息丢失（需求 3.2） |
| D5 | 事务提交后（afterCommit）才发 Kafka | MySQL 是事实源头，防止库回滚但消息已发出 |
| D6 | update 局部落库，消息用回查后的完整行 | 防止请求未传字段以 null 覆盖 Redis 完整数据 |
| D7 | 营销系统不读 MySQL；Redis miss 返回空列表 | 职责分离；补偿任务保证最终有数据 |
| D8 | Kafka 消息为纯 JSON（关闭类型头），消费端手动 Jackson 解析 | 跨系统解耦，避免反序列化配置坑 |
| D9 | 消息幂等：消息体预留 messageId，显式去重标 TODO（需求 3.1） | 当前靠 version 比对保证重复消费结果不变 |
| D10 | 消费消息时先采集旧范围与新范围对应的 localCache key，再执行 Redis 变更 | Banner 修改有效日期或业务线时，同时失效旧、新本地缓存，避免旧 key 的本地缓存继续返回过期数据 |
| D11 | Banner 定向用户名单独立存储：MySQL 每行最多 1000 个 userId，Kafka 不携带名单，营销系统按 bannerId 回源 CRM | 控制消息体大小；Redis 使用 Set 分桶 + bucket 索引，查询时先按时间再按 userId 过滤；空名单表示所有用户可见 |

## 4. 待办

- [ ] **运行时联调**：按「下一步」走通 写 → 同步 → 读 全链路
- [ ] **消息幂等实现**：Redis SETNX 按 messageId 去重（代码 TODO 已标注在 `BannerChangeConsumer`）
- [ ] 补偿任务优化：全量重发 → 只重发近期有变更 / 未同步的 banner（数据量增大后 5 分钟全量会成为瓶颈）
- [ ] 全局异常处理：目前参数错误直接抛 `IllegalArgumentException`（返回 500），加 `@RestControllerAdvice` 转 400
- [ ] （可选）单元测试：优先覆盖 `BannerRedisService` 的乱序 / 墓碑逻辑
- [ ] 增加缓存失效测试：验证 banner 修改业务线或有效日期时，旧、新范围对应的 localCache key 都会失效
- [ ] 增加用户定向测试：验证空名单全量可见、指定 userId 可见性、MySQL/Redis 1000 条分片和 banner 更新删除后的 Redis 桶清理

## 5. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 本机 Maven 默认 JDK 8，Spring Boot 3.5 需 17 | 编译失败 | 构建前设 `$env:JAVA_HOME="D:\develop_tools\jdk\jdk17"`（已验证） |
| Kafka 发送失败仅记日志不重试 | 实时消息丢失 | 补偿任务 5 分钟内全量重发自愈 |
| 多实例部署营销系统时 localCache 只在单实例失效 | 其他实例最长 30s 脏读 | Caffeine TTL 30s 兜底；单实例无此问题 |
| version 取 CRM 机器毫秒时间戳 | 多机 CRM 时钟回拨导致版本倒退 | 当前单机运维可接受；多机需改 DB 序列或雪花 ID |
| schema.sql 仅在 MySQL 首次建卷时自动执行 | 改表结构不生效 | `docker compose down -v` 重建卷，或手动执行 DDL |
| localCache 失效范围依赖 Redis 中的旧消息 | 若旧 `banner:data:{id}` 已过期或不存在，无法计算旧范围；旧 Hash 数据可能仍需依赖 TTL/补偿清理 | 当前 `banner:data` 无 TTL；后续增加对账任务或保存 banner 历史范围 |
| PowerShell 5.1 中 `curl` 是 Invoke-WebRequest 别名 | 验证命令失败 | 命令统一用 `curl.exe` |
| 营销系统通过 HTTP 回源 CRM 获取名单 | 消费吞吐受 CRM 接口、网络和名单大小影响；接口失败会导致定向缓存未更新 | 增加超时、重试、监控；当前示例实现先保持链路简单 |
| Redis audience bucket 更新不是事务 | 更新过程中查询可能读到旧桶或部分新桶 | 先删除旧桶再写新桶；后续可用版本化索引或 Lua/双版本切换 |

## 6. 下一步

按顺序执行，每步可直接复制运行：

1. 启动中间件并确认容器状态：

   ```powershell
   docker compose up -d
   docker compose ps
   ```

2. 启动双服务（两个终端窗口各跑一条）：

   ```powershell
   $env:JAVA_HOME="D:\develop_tools\jdk\jdk17"; mvn spring-boot:run -pl banner-crm
   ```

   ```powershell
   $env:JAVA_HOME="D:\develop_tools\jdk\jdk17"; mvn spring-boot:run -pl banner-marketing
   ```

3. 验证全链路（写 → 同步 → 读）：

   ```powershell
   # 新增 banner（业务线 1=助农产品，当前起 7 天有效；示例数据占用了 id 1-4，新记录应为 id=5）
   curl.exe -X POST http://localhost:8081/crm/banner -H "Content-Type: application/json" -d '{"bizId":1,"title":"助农水果节","imageUrl":"https://cdn.example.com/banner/fruit.png","jumpUrl":"https://live.douyin.com/99999","startTime":"2026-09-10T00:00:00","endTime":"2026-09-17T23:59:59","sort":1}'

   # 用户侧查询（应返回该业务线"此刻"可见 banner，含 jumpUrl）
   curl.exe "http://localhost:8082/marketing/banners?bizCode=agri"

   # Redis 数据形状检查
   docker exec banner-redis redis-cli HGETALL banner:agri:20260910
   docker exec banner-redis redis-cli GET banner:data:5
   docker exec banner-redis redis-cli TTL banner:agri:20260910
   ```

4. 编写并运行缓存失效测试：验证 banner 修改业务线或有效日期时，旧、新范围对应的 localCache key 都会失效；完成后勾掉「待办」中的缓存失效测试项。
5. 编写并运行用户定向测试：验证空名单、指定 userId、1000 条分片、更新/删除后的桶清理；完成后勾掉「待办」中的用户定向测试项。
6. 联调通过后：勾掉「待办」第 1 项与「当前状态」的联调行，把验证中发现的问题补进「待办」/「风险」。
