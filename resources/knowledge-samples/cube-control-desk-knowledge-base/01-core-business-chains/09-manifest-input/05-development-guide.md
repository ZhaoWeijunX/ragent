---
module: manifest-input
title: Manifest Input 开发指南
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest Input 开发指南

新增官网字段先改标准 DTO/Mongo codec，再在 `ManifestInputRuleStrategy` 或船司策略校验；清洗和必填分组放 Processor/validation context；账号、任务、当前态分别复用 Handler/Task/Record service。不要把 COSCO 规则复制进通用策略，也不要把 Intake 的 BL 来源字段写入通道记录而无契约。

必须保持：identity 唯一性、`data_id` 快照可追溯、submitProcess 与 status 分离、操作历史幂等、失败可重提、tenant/cid 隔离。回执处理要测试重复 receipt、过期 taskNo、旧版本重提、未知状态和通知失败。涉及 dispatch 时同时检查 `ManifestApiTaskFairDispatchService` 的筛选、账号容量和任务查询；具体容量算法不能凭通用调度知识补写。

面试深挖：为什么当前态和 Mongo 快照分离？为什么操作历史需 hash？如何避免重复回执推进两次？为何公平调度不能直接由 Controller 调用？回答应引用本仓库 handler、SQL 唯一键和 receipt transaction。源清单：Processor/rule/handler/dispatch/receipt、SQL、DTO。

## 文档与代码差异

设计允许通过 Registry 增加策略，但当前源码只有 COSCO 官网策略可作为可运行实现证据。新增船司时不能只改配置：必须同时新增策略、规则测试、账号/通道验证和回执契约验证，并更新设计与覆盖矩阵。

## 并发、事务与回滚

新增或替换当前动作时，必须把数据库唯一约束作为并发保护，不能只用一次查询判断不存在。回执事务只保证本地数据库异常回滚，Mongo 快照、外部官网副作用和通知仍需补偿或重试。事务边界变化应补测重复提交、旧任务回执、快照写失败和通知失败，并区分代码事实与推断。

## 推荐改动顺序与审查点

1. 先扩展 API model 和 `ManifestData` 序列化测试，确认嵌套货物、箱信息和身份字段不丢失。
2. 在 `ManifestOpenApiDataConvert`/Processor 处理默认值、格式清洗和上下文校验；依赖 carrier、channel、账号的规则不能塞进无上下文 DTO。
3. 在 Registry 注册船司策略，用 `COSCOWebManifestInputRuleStrategyTest` 锁定必填、长度、枚举和跨字段规则。
4. 通过 `ManifestRecordHandler` 统一写快照、当前记录和任务；回执改动同步检查状态目标、迟到判断、监控任务和通知。

必须保持 identity 唯一性、data_id 可追溯、submitProcess/status 分离、operationHash 幂等和 cid 隔离。审查至少覆盖重复 receipt、旧 taskNo、未知状态、Mongo 写入异常、通知异常和逻辑删除后重建。新增船司不能只改配置，必须有策略、账号/通道、规则和回执测试；生产容量与官网协议当前代码无法确认。
