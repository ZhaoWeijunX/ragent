---
title: 独立 VGM Input 模块概览
module: vgm-input
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 独立 VGM Input 模块概览

VGM Input 是独立的官网 VGM 填写、校验、提交、回执和监控能力。Web 入口为 `BizCommandVgmInputController`，内部入口为 `ICommandVgmInputOpenApi`/`CommandVgmInputOpenApiProvider`，编排为 `BizCommandVgmManager`，确认处理为 `VgmInputConfirmProcessor`。

主数据为 `biz_vgm_record`、`biz_vgm_container` 与 Mongo 快照；任务命令为 `TaskCommandEnum.VGM_INPUT`。它和接单侧 `vgm_info` 是两个数据域。

## 职责与运行位置

`BizCommandVgmInputController` 处理 Web 采集、查询、提交和列表；`CommandVgmInputOpenApiProvider` 提供内部 submit/monitor/query/receipt；`BizCommandVgmManager` 组织账号、官网查询、Processor、任务和回执；`AbstractVgmInputProcessor`/`VgmInputConfirmProcessor` 固定清洗校验与保存模板；`VgmRecordHandler` 集中推进记录和容器状态。

规则由 `VgmInputRuleTools` 按 carrier/channel 选择策略，当前可确认 `COSCO_WEB_VgmInputRuleStrategy`。官网状态查询集中在 `VgmWebsiteInfoQueryService`，最终通过 `ClusterOpenApiService.queryVgmInfoApi` 调用执行集群。

## 设计、风险与非目标

该能力只维护官网执行事实，不拥有接单侧人工工单。任务与业务记录分离，便于调度、超时轮询和回执幂等；MySQL 记录/容器与 Mongo 快照分离，便于查询和结构演进，但需要补偿跨存储部分失败。

Provider 的 Redis 锁保护 submit/monitor/receipt 并发窗口，不能替代历史幂等；重复或晚到回执还要依赖 taskNo 和旧状态条件。当前代码无法确认生产集群 SLA、账号容量、调度周期与其他船司策略。

## 差异、面试与来源

设计文档展示可扩展能力，不代表所有船司已实现；当前源码能直接证明的船司规则以 Registry 实例为准。面试可追问任务状态与业务状态、锁与幂等、轮询与回调的取舍。来源：Controller、Provider、Manager、Processor、RuleTools/COSCO strategy、Handler、WebsiteQueryService、实体/枚举、`sql/vgm/`。
