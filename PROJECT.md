# PROJECT.md — 项目状态看板

> **维护规则**：每次任务开始先读本文件；完成工作后只更新发生变化的内容。
> 发现遗漏任务补充到「待办」，重复信息就地合并，「下一步」必须始终可直接执行。
> 最后更新：2026-09-20

## 1. 项目目标

抖音电商平台 banner 模块（只做 banner 业务，不含登录 / 商品 / 订单 / 前端）：

- **B 端 · CRM 系统（:8081）**：运维对业务线、banner 增删改查，写 MySQL，事务提交后发 Kafka 变更消息
- **同步链路**：Kafka（`banner-topic`）传递变更消息，营销系统消费后写入 Redis
- **C 端 · 营销系统（:8082）**：用户查询"此刻可见"的 banner 列表（localCache → Redis），点击后按 jumpUrl 跳转（直播间 / 活动页 / 商品详情页）

## 2. 当前状态

**三 Key 重构代码完成，JDK 17 编译通过（2026-09-20），尚未做运行时联调。**

| 事项 | 状态 |
| --- | --- |
| banner-common（实体 / 消息体 / 枚举 / 常量） | ✅ 完成 |
| banner-crm（CRUD + 发消息 + 补偿任务，:8081） | ✅ 完成 |
| banner-marketing（消费 + Redis + localCache 查询，:8082） | ✅ 完成 |
| docker-compose.yml / schema.sql（含示例业务线）/ README.md（架构、数据设计、可执行快速开始） | ✅ 完成 |
| `mvn clean install`（JDK 17） | ✅ BUILD SUCCESS，无 lint 错误 |
| 运行时联调（中间件 + 双服务 + 全链路验证） | ⬜ 未开始 |

## 3. 关键决策

| # | 决策 | 理由 |
| --- | --- | --- |
| D1 | Spring Boot 3.5.16 + JDK 17，Maven 单仓库多模块 | 已确认；双系统共享实体与消息体最简单 |
| D2 | Redis Key 1：`banner:{bannerId}`，String 保存 Banner 状态 | 保存 version、deleted、buckets；按 endTime 次日零点过期，用于乱序判断 |
| D3 | Redis Key 2：`banner:{bizCode}:{yyyyMMdd}`，Hash 保存 `bannerId -> BannerMessage` | 业务日期查询入口，按日期次日零点过期，并整体缓存到 localCache |
| D4 | Kafka 消息 key=bannerId + 数据库乐观锁 version + deleted 状态 + 定时补偿/Outbox | 解决更新并发、消息乱序、物理删除后的迟到消息复活和消息丢失 |
| D5 | 事务提交后由 Outbox 投递 Kafka | 业务写入/删除与事件记录同事务；Kafka 发送失败可重试 |
| D6 | update 局部落库，消息用回查后的完整行 | 防止请求未传字段以 null 覆盖 Redis 完整数据 |
| D7 | 营销系统不读 MySQL；Redis miss 返回空列表 | 职责分离；补偿任务保证最终有数据 |
| D8 | Kafka 消息为纯 JSON（关闭类型头），消费端手动 Jackson 解析 | 跨系统解耦，避免反序列化配置坑 |
| D9 | 消息幂等：消息体预留 messageId，显式去重标 TODO（需求 3.1） | 当前靠 version 比对保证重复消费结果不变 |
| D10 | 本地缓存采用非强一致策略，不由 Kafka 消费者主动失效 | Banner 变更后允许最多约 30 秒脏读，由 TTL 控制 |
| D11 | Banner 定向用户名单独立存储：MySQL 每行最多 1000 个 userId，Kafka 不携带名单，营销系统按 bannerId 回源 CRM | 控制消息体大小；Redis 使用 Set 分桶；空名单表示所有用户可见 |
| D12 | Banner 版本使用数据库乐观锁递增；更新/删除必须携带 expected version | 多并发修改时只有 `WHERE id=? AND version=?` 成功的一方可以提交，避免时间戳回拨和覆盖 |
| D13 | DELETE 采用 MySQL 物理删除，先递增版本并把 `deleted=true` 事件写入 Outbox；Redis 保留 `banner:{id}` 删除状态 | 既不保留主表脏数据，又能让营销系统拒绝迟到旧消息；Outbox 负责删除消息补偿 |
| D14 | Redis 采用三 Key：`banner:{id}` 单 Banner 状态、`banner:{bizCode}:{yyyyMMdd}` 业务日期 Map、`banner:{id}:bucketIndex:{n}` 用户 Set 桶 | 消除冗余辅助前缀；单 Banner Key 负责版本，业务日期 Map 负责查询，桶 Key 解决人群包 Big Key |
| D15 | `buckets` 是有效桶数量；减少桶时只收窄索引范围，不主动删除超范围旧桶 | 降低 Redis 删除压力，旧桶按 endTime 次日零点 TTL 自动清理 |
| D16 | localCache 只缓存业务日期 Map，不缓存用户维度结果 | 避免本地缓存按用户数量膨胀；用户过滤在命中 Map 后按 buckets 查询 |

## 4. 待办

- [ ] **运行时联调**：按「下一步」走通 写 → 同步 → 读 全链路
- [ ] **消息幂等实现**：Redis SETNX 按 messageId 去重（代码 TODO 已标注在 `BannerChangeConsumer`）
- [ ] **乐观锁/删除事件测试**：验证相同 version 并发更新只有一个成功、旧 UPDATE 晚于 DELETE 不复活、Outbox 能补偿 DELETE
- [ ] 补偿任务优化：全量重发 → 只重发近期有变更 / 未同步的 banner（数据量增大后 5 分钟全量会成为瓶颈）
- [ ] 全局异常处理：参数错误返回 400，版本冲突返回 409
- [ ] 增加用户定向测试：验证空名单全量可见、指定 userId 可见性、MySQL/Redis 1000 条分片和 banner 更新删除后的 Redis 桶清理

## 5. 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 本机 Maven 默认 JDK 8，Spring Boot 3.5 需 17 | 编译失败 | 构建前设 `$env:JAVA_HOME="D:\develop_tools\jdk\jdk17"`（已验证） |
| Kafka 发送失败或 Outbox 投递失败 | 事件延迟，Redis 可能暂时未同步 | Outbox 保留 PENDING/FAILED 事件并按退避策略重试 |
| 多实例部署营销系统时 localCache 只在单实例失效 | 其他实例最长 30s 脏读 | Caffeine TTL 30s 兜底；业务接受非强一致 |
| Redis 桶减少采用懒删除 | 超范围旧桶在 TTL 前继续占用空间，但查询不可见 | `buckets` 限制扫描范围；按 endTime 次日零点自��过期 |
| 本地缓存非强一致 | Banner 变更或人群包更新后，用户最多在 TTL 内看到旧业务日期 Map | localCache 只缓存 Key 2，默认 TTL 30 秒 |
| 桶写入不是 Redis 事务 | Consumer 更新期间可能短暂读到中间状态 | 按 buckets 增减顺序写入；后续可用 Lua 或版本化 Key |
| schema.sql 仅在 MySQL 首次建卷时自动执行 | 改表结构不生效 | `docker compose down -v` 重建卷，或手动执行 DDL |
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

3. 按 `README.md` 的「5.4 创建一个带定向名单的 Banner」到「5.7 验证更新与删除的版本控制」依次执行，完成 写入 → Outbox/Kafka → Redis → 用户查询 → 更新/删除验证。
4. 将运行结果与 README 中每步的预期结果对照；若查询为空，按 README 的 Redis 三 Key 命令定位同步、人群桶或日期 Key。
5. 编写并运行用户定向测试：验证空名单、指定 userId、1000 条分片、更新/删除后的桶清理；完成后勾掉「待办」中的用户定向测试项。
6. 联调通过后：勾掉「待办」第 1 项与「当前状态」的联调行，把验证中发现的问题补进「待办」/「风险」。
