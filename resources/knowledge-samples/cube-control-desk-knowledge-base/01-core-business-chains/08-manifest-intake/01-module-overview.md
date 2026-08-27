---
module: manifest-intake
title: Manifest 接单模块概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest 接单模块概览

## 目的、位置与边界

Manifest 接单是从 BL 接单记录生成、编辑并跟踪舱单业务的页面门面。HTTP 入口是 `ManifestEntrustedInfoController`，编排集中在 `ManifestEntrustedInfoManagerImpl`，当前态落 MySQL `manifest_entrusted_info`，表单详情落 `ManifestEntrustedDetailDocument` 对应 Mongo 文档，提交尝试落 `manifest_entrusted_submit_record`。它不拥有官网任务执行、账号调度或通道状态机；这些属于 Manifest Input，通过 `ManifestSubmissionService`/OpenAPI 协同。

## 核心职责

- `page/statusCount/detail`：按当前租户查询接单当前态并补充 BL、邮件、客户和异常展示信息。
- `createFromBill`：校验来源 BL、船司唯一键和可创建状态，在 Redis 锁内创建接单记录及详情快照。
- `save/submit/close/remark`：维护可编辑状态、版本化详情、提交快照和操作日志。
- `queryConfiguration/queryManifestInfo/queryHsCodes/queryCustomerTypes`：向页面提供配置或透传通道查询。

## 设计要点

表中的 `unique_key_active` 由非关闭状态生成，配合 `(cid, carrier, unique_key_rule, unique_key_value, unique_key_active)` 唯一索引阻止同一业务键并发重复创建；逻辑删除记录仍通过专用查询参与业务唯一性判断。提交详情使用版本号和不可变快照，避免用户修改当前草稿后回调误写另一版本。

## 非目标与证据边界

本模块不等于 Manifest Input，也不等于 BL 主单状态机。代码能确认的来源主要是 `createFromBill` 的 BL 校验和当前实体字段；页面展示字段的完整业务含义应以 VO/转换器继续核对。当前仓库未确认生产外部船司接口的实时成功率、SLA 或业务规则。

## 源清单

`ManifestEntrustedInfoController`、`ManifestEntrustedInfoManagerImpl`、`ManifestEntrustedDetailService`、`ManifestEntrustedTransactionService`、`ManifestEntrustedCallbackManagerImpl`、`ManifestEntrustedInfo`/`ManifestEntrustedSubmitRecord`、`sql/manifest/manifest_entrusted.sql`。

## 文档与代码差异

`doc/design/manifest/manifest_entrusted_design.md` 描述目标业务方案；本文只把当前 Controller、Manager、事务服务、实体和 SQL 已出现的能力写成现状。设计中的前端交互或迁移步骤若没有对应入口/写入，仍属于设计或历史证据，不据此认定已上线。
