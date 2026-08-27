---
module: manifest-input
title: Manifest Input 数据与状态
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest Input 数据与状态

`biz_manifest_record` 是通道当前态，字段包括 `data_id`（Mongo 快照）、`customer_task_id`、`business_no`、`identity_type/value`、carrier/channel、account_id/account 快照、`submit_process`、船名航次、港口预留字段、状态、官网最新操作、错误和 submit 时间。有效记录通过 `(cid, carrier, identity_type, identity_value, identity_active)` 唯一约束去重；逻辑删除不应被当作可见当前态。

`ManifestInputStatusEnum`：10 提交中、20 已保存官网草稿、40 已提交官网、50 已接受申报、90 提交失败。`ManifestSubmitProcessEnum` 区分本次官网动作，不等于 status；`ManifestIdentityTypeEnum` 表示关单号或订舱号业务身份。操作历史单独写 `biz_manifest_operation_record`，以 manifestId + operationHash 幂等，保存名称、时间、官网原文状态和描述。

任务壳 `BizCustomerTask` 承担调度生命周期，不能用任务状态替换 manifest status。提交回执更新当前态，操作回执更新 latest operation 与历史。事务边界由 receipt service/handler 实现，通知独立发送。当前代码无法确认 Mongo 集合名、分布式调度锁的线上 TTL 和状态迁移失败后的补偿策略。

源清单：`ManifestInputStatusEnum`、`BizManifestRecord`、`BizManifestOperationRecord`、两个 SQL、ReceiptTransactionService、ManifestRecordHandler、Task 实体。

## 文档与代码差异

部分历史/设计描述会把 `submit_process` 当作完整生命周期状态，或把 `BizCustomerTask` 状态当作 Manifest 当前态；当前代码明确将三者分开：`submit_process` 表示本次动作，`BizCustomerTask` 表示调度生命周期，`biz_manifest_record.status` 才是通道业务当前态。另有材料可能按“业务号唯一”概括去重，实际 SQL 唯一约束包含 `cid + carrier + identity_type + identity_value + identity_active`，并利用 active 标识兼容逻辑删除。出现差异时以实体、枚举和当前 SQL 为准。

## 一致性与验证重点

`biz_manifest_operation_record` 以 operationHash 约束重复官网操作事实，但它不能替代主记录状态条件更新；操作回执与提交回执并发时，应验证 latest operation 不会把已进入终态的主记录回退。MySQL 主记录与 Mongo `data_id` 指向的标准快照属于跨存储关联，当前代码无法确认二者具备原子提交或自动补偿，因此排障必须同时保留 manifestId、dataId、customerTaskId 和 identity 四组定位键。

测试应覆盖有效记录重复创建、逻辑删除后重建、重复 operationHash、旧任务回执、提交失败后更新重提，以及 MySQL 成功而快照/通知失败的边界。本文截至 2026-08-26 只有源码、枚举和 SQL 静态证据，生产索引实际状态、Mongo 集合配置和分布式锁 TTL 仍需运行态确认。

## 索引、快照与状态安全

`idx_cid_account_status` 支持账号维度处理中记录扫描，`idx_manifest_dispatch` 支持调度层按 status/deleted/carrier/channel/account 筛选，`uk_manifest_identity` 将 `identity_active` 纳入唯一键，使逻辑删除后可以创建新有效记录。`biz_manifest_operation_record` 通过 `uk_manifest_operation(manifest_id, operation_hash)` 去重官网事件；它保存事件摘要，不保存完整发送快照。

| 维度 | 代码含义 | 不能替代 |
| --- | --- | --- |
| `submit_process` | 本次官网动作 | 官网最终状态 |
| `biz_manifest_record.status` | 通道当前态 | 任务调度生命周期 |
| `BizCustomerTask` | 一次外部执行任务 | 官网接受申报事实 |
| operation history | 官网操作证据 | 主记录状态推进 |

乱序回执必须同时比较 taskNo、动作版本、operationTime、当前 status 和目标状态允许性。生产是否有跨任务重放窗口、人工修复和自动补偿，当前代码无法确认；MySQL 与 Mongo 也不具备已证明的原子提交。
