---
module: manifest-intake
title: Manifest 接单开发指南
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest 接单开发指南

## 改动定位

先从 Controller 路由确定页面契约，再沿 `Manager -> EntrustedInfoService/DetailService -> Mapper/Repository` 跟踪。涉及提交时追加 `ManifestSubmissionConfigResolver`、`ManifestSubmissionService`、回调 manager、提交记录和操作日志；涉及来源时检查 BL 状态、详情、异常和来源 ID 过滤。

## 必须保持的规则

1. 创建唯一性必须复用 `ManifestCreateUniqueKeyStrategyRegistry` 与 Redis 锁，不能只依赖 Java `exists`。
2. 详情修改必须遵循版本检查，提交发送不可变快照，不能把 Mongo 当前文档引用当历史证据。
3. 接单状态枚举和通道状态枚举分别维护；映射只能在明确的 callback/service 边界完成。
4. 新船司规则放 unique-key/config/rule 扩展点，避免在 manager 写 carrier if/else；当前可确认的策略至少包括 COSCO 相关实现。
5. 查询必须带租户和 owned-record 条件；批量页面展示不得用未过滤的 BL 附件或客户数据。

## 事务与并发

创建是“分布式锁 + 数据库唯一索引”双保险；保存、提交记录和回调应使用现有事务服务。通知在回执本地事务之后独立发送，失败时依赖可重试通知，不应重复推进状态。修改计划式地替换详情/提交时，先确认事务边界和异常回滚行为。

## 开发验证

最小回归应覆盖：来源 BL 不存在、重复唯一键、关闭后重建、提交失败重提、旧版本提交、重复回执、跨租户 ID 访问。静态检查所有新字段是否同步 DTO、Mongo codec、VO、SQL 与 operation snapshot；真实 API-test 主链当前代码未在本稿中确认。

## 面试深挖

可追问：为什么锁还需要唯一索引？逻辑删除如何参与去重？当前态、快照和提交尝试分别解决什么问题？通知为何不放在同一事务里？如何防止旧回调覆盖新重提？答案均应回到上述类和 DDL，而不是泛化为“用了分布式锁”。

## 源清单

Manager、TransactionService、UniqueKey registry、RedisLockUtil、实体/Mapper、回执 manager、Manifest DDL。

## 文档与代码差异

任务包和迁移清单可用于复现演进顺序，但不能证明 unique key、锁、回执或数据迁移已在所有环境生效。开发时以当前 DDL、实体、Registry 和事务代码为准；环境执行状态需由数据库变更记录和 api-test 另行确认。
