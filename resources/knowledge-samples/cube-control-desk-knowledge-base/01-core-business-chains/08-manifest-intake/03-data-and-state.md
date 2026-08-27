---
module: manifest-intake
title: Manifest 接单数据与状态
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest 接单数据与状态

## 数据分层

`manifest_entrusted_info` 是页面当前态：`cid`、来源 BL ID/工单 ID、`manifest_no`、船司/账号/订舱号、MASTER/HOUSE、关单号、唯一键、`biz_status`、`task_no`、当前提交快照和错误摘要均在此。详情表不承载整份表单，而由 `ManifestEntrustedDetailDocument`/`ManifestEntrustedDetailService` 维护版本化 `ManifestData`。`manifest_entrusted_submit_record` 记录每次 `attempt_no`、`business_no`、快照 ID、详情版本、taskNo 和受理状态；这是审计与回调定位依据，不是页面当前态替代物。

## 接单状态

`WAIT_SUBMIT=2`、`SUBMITTING=10`、`SUBMIT_FAILED=11`、`SAVED_TO_WEBSITE_DRAFT=12`、`SUBMITTED_TO_WEBSITE=20`、`ACCEPTED_DECLARATION=30`、`CLOSED=99`。可编辑集合由枚举 `editableStatuses()` 固定为 2/11/12；关闭后不可再次创建同一唯一键。状态转换必须通过 manager/transaction/callback，不应直接改表。

## 一致性机制

唯一索引防止同一租户、船司、策略版本和业务值存在多个未关闭记录；`unique_key_active` 将关闭态映射为 NULL，使关闭记录释放数据库唯一占位，但 manager 仍显式查询包含逻辑删除数据，避免删除绕过业务去重。提交详情版本、提交快照 ID 与尝试号三者共同回答“哪一版数据被发送”。操作历史在 `ManifestOperationReceiptService` 中以业务哈希幂等，具体哈希字段见通道侧操作表。

## 边界

接单 `biz_status` 与通道 `ManifestInputStatusEnum`（10/20/40/50/90）编码不同，不能混传；`task_no` 只是关联键，不代表回调一定成功。数据库 DDL 显示 `manifest_no` 与 taskNo 有唯一约束，taskNo 为空时仍需结合提交记录和业务日志判断。

## 未知/差异

现有简略草稿未列出编码；本稿以枚举与 DDL 为准。当前代码无法确认 Mongo 集合名及线上索引实际是否与提交 SQL 完全一致。

## 源清单

`ManifestEntrustedStatusEnum`、`ManifestEntrustedInfo`、`ManifestEntrustedSubmitRecord`、`ManifestEntrustedDetailDocument`、`ManifestEntrustedTransactionService`、`manifest_entrusted.sql`。

