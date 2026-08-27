---
module: manifest-intake
title: Manifest 接单排障与验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest 接单排障与验证

## 证据顺序

先记录请求路径、租户 cid、来源 BL ID、manifest ID、taskNo、attemptNo；再查 `manifest_entrusted_info` 当前态、详情文档版本、`manifest_entrusted_submit_record` 提交尝试和操作日志。不要只看页面状态，也不要拿通道 `biz_manifest_record.status` 直接当接单状态。

## 常见故障

- 创建重复：核对 carrier、unique-key rule/value、`unique_key_active`，并检查锁键日志；逻辑删除不能绕过 manager 的包含删除查询。
- 提交后一直提交中：用 taskNo 反查接单提交记录及通道任务；确认回执路由、异步 Job/dispatch，而非重复点击 submit。
- 回执覆盖错误：比较 submitSnapshotId、detailVersion、attemptNo 和当前重提版本；旧回执应被 callback 幂等/一致性规则拒绝或不改变新尝试。
- 页面详情不一致：核对 MySQL 当前态与 Mongo detail 的 version/dataId，检查保存事务是否回滚。
- 跨租户可见性：复现 detail/page 的 cid 条件和 `requireOwned`，记录真实 SQL；Controller 的 `ApiPermission(false)` 不能作为安全结论。

## 验证命令与边界

可用 `rg -n "ManifestEntrusted|manifest/submit|manifestInputReceipt"` 定位路由和调用；用 SQL DDL 核对索引。当前仓库扫描确认代码/模型/SQL，但未执行外部船司、Cluster 或稳定 API-test 主链，因此只能报告静态证据，不能宣称线上回执验证通过。

## 差异、未知项与交接

旧草稿将接单和 Input 合并描述，已按代码拆开。当前代码无法确认生产消息积压、第三方响应原文及通知重试结果；交接时附请求日志、四类 ID、数据库快照时间和回执原文。

## 源清单

Controller/Manager、Transaction/Callback/Receipt 服务、两张接单 SQL 表、通道契约和枚举。

