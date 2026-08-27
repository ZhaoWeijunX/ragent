---
module: manifest-input
title: Manifest Input 模块概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest Input 模块概览

Manifest Input 是官网舱单填写/提交的通道侧能力，接收内部 OpenAPI 请求，保存 `biz_manifest_record` 当前态与 Mongo 标准数据，生成 `BizCustomerTask` 后由 Manifest dispatch 下发。契约是 `ICommandManifestInputOpenApi`，实现是 `CommandManifestInputOpenApiProvider`；核心处理在 `AbstractManifestInputProcessor`、`ManifestInputConfirmProcessor`、`ManifestInputUpdateProcessor`，规则由 `ManifestInputRuleStrategyRegistry`/`COSCO_WEB_ManifestInputRuleStrategy` 扩展。

它与 Manifest Intake 的边界是“通道执行记录 vs 接单门面”：Input 只负责账号/通道提交、官网查询和回执，不维护 BL 来源关系和页面接单状态。当前实现能确认的通道主要围绕 channel、carrier 和账号；外部官网交互由任务集群完成，仓库无法确认其真实页面协议。`biz_manifest_record` 的 identity 唯一索引和 `customer_task_id` 是任务关联关键。

源清单：`CommandManifestInputOpenApiProvider`、`ICommandManifestInputOpenApi`、manifest core processor/rule/handler/dispatch/receipt、`biz_manifest_record.sql`、API manifest_input DTO。未知项：生产 Cluster 的执行器版本、实际并发额度和认证方式当前代码无法确认。

## 文档与代码差异

现有设计材料容易把 Manifest 接单门面与通道提交能力合并描述；当前代码已经拆成两套当前态和入口：接单侧使用 MySQL `manifest_entrusted_info` 与 Mongo `manifest_entrusted_detail`，通道侧使用 `biz_manifest_record` 与其 `data_id` 关联的 Mongo 标准快照。另一个容易过度概括的点是船司扩展能力：当前源码只检索到 `COSCO_WEB_ManifestInputRuleStrategy`，因此不能依据通用 Registry 设计宣称所有 carrier/channel 已实现。发生冲突时，以本节列出的当前 Provider、Processor、策略和 SQL 为准。

## 运行边界与深挖

MySQL 当前态、Mongo 标准数据、`BizCustomerTask` 和外部官网副作用不处于一个可证明的全局事务中。唯一索引解决的是同一租户、船司和业务身份的本地重复记录，不能提供外部 exactly-once；重复提交、旧 task 回执和乱序操作回执仍要依赖 handler 的状态前置条件和 operationHash 幂等。当前仓库未提供生产 Cluster 协议、并发配额或回调重放窗口，相关行为必须以运行日志补证。

面试可从三点展开：为何 Intake 与 Input 分离当前态；唯一索引、业务锁和回执幂等分别防止哪类竞态；外部调用成功但本地事务失败时如何以任务、回执和操作历史收敛。本文截至 2026-08-26 仅完成源码、配置、模型与 SQL 静态核验，未执行真实 API-test 或外部官网调用。

## 组件职责与依赖关系

`ICommandManifestInputOpenApi` 定义提交、配置查询与任务/操作回执契约；`CommandManifestInputOpenApiProvider` 将 API DTO 转为内部命令并交给 `ManifestSubmissionService`。Provider 不应直接写 MySQL 或 Mongo，否则会绕过处理器的标准化、船司规则和当前动作替换。

`ManifestRecordHandler` 协调 `ManifestMysqlPersistenceService`、`ManifestMongoSnapshotRepository` 与 `BizCustomerTask`：MySQL 保存可检索当前态，Mongo 保存完整标准快照，CustomerTask 保存一次外部执行动作的调度身份。`idx_manifest_dispatch` 服务于状态、删除标识、通道和账号筛选，不是官网成功证明。

`ManifestReceiptTransactionService.handle` 位于 `@Transactional(rollbackFor = Exception.class)` 边界内，校验迟到/重复回执并映射草稿或提交状态；成功后按配置创建监控 ScheduleJob，失败写业务错误。通知由 `ManifestNotificationDispatcher` 后置发送，通知异常不应回滚已落库状态。

## 验证入口

可用 `ManifestRecordHandlerTest`、`ManifestMysqlPersistenceServiceTest`、`ManifestReceiptTransactionServiceTest`、`ManifestApiTaskFairDispatchServiceTest` 和 `COSCOWebManifestInputRuleStrategyTest` 分别验证持久化、回执、调度与规则分支；这些测试不覆盖外部官网。

## 设计取舍与边界

官网动作需要完整可审计快照，而列表、调度和回执需要按租户、身份、状态、账号低成本查询，因此 MySQL 当前态与 Mongo 快照分工。`data_id`、`customer_task_id` 和 operationHash 形成记录—快照—外部动作—官网事件证据链；代价是跨 MySQL、Mongo 和外部官网不存在可证明的全局事务，必须靠回执和补偿收敛。

当前源码只能确认 `COSCO_WEB_ManifestInputRuleStrategy`，不能推断所有船司可提交。生产执行器版本、真实并发额度、账号限流、Mongo 集合和补偿策略当前代码无法确认。
