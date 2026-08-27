---
title: 独立 VGM Input 数据与状态
module: vgm-input
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 独立 VGM Input 数据与状态

## 主记录、容器与快照

`biz_vgm_record`/`BizVgmRecord` 是通道侧主记录，保存租户、船司、订舱号、账号、taskNo、提交状态、官网状态和错误；`biz_vgm_container`/`BizVgmContainer` 保存逐箱 VGM 数据及官网提交状态。Mongo `ODS_VGM_RECORD` 保存结构化提交快照，用于重显和审计，不复用 Intake 的 `vgm_detail`。

任务仍写 `biz_task`、`biz_customer_task`，命令为 `TaskCommandEnum.VGM_INPUT`。任务状态描述本次执行，`VgmInputStatusEnum` 描述业务记录当前阶段；两者需要通过 taskNo 关联但不能混为同一状态。

## 状态来源

`VgmRecordHandler` 处理提交回执，`VgmWebsiteSubmitStatusTypeEnum` 表达官网查询状态，`VgmSubmitStatusPollJob` 轮询仍在提交中的记录，`VgmSubmittingTimeoutMonitorJob` 处理超时观察。容器级状态可能与主记录聚合状态不同，页面/回调逻辑应明确聚合规则，不能用任意一个容器覆盖整票。

## 幂等与一致性

- Provider 用 submit/monitor/receipt lock key 降低并发重入；锁释放后仍需 taskNo、业务唯一条件和状态守卫。
- MySQL 主/容器与 Mongo 快照跨存储，需保留 data/task 标识支持补偿。
- 轮询和回调可同时观察官网状态，旧观察不得覆盖已完成终态。
- 接单侧只保存关联 taskNo/投影，不能直接更新本表作为人工操作结果。

## 差异、未知与来源

旧文档可能把 VGM Input 状态当作接单 VGM 状态；当前表和 Handler 证明两者独立。当前代码无法确认生产唯一索引、轮询周期、历史数据完整性和外部官网状态语义的全部船司差异。来源：`BizVgmRecord`、`BizVgmContainer`、Mongo model/convert、`VgmInputStatusEnum`、WebsiteStatus enum、Handler、Poll/Timeout Job、任务实体与 VGM SQL。

面试追问：主/容器状态如何聚合、轮询与回调如何防状态倒退、为什么锁不是 exactly-once。
