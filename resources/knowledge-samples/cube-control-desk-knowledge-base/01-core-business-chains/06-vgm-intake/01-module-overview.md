---
title: 接单侧 VGM Intake 模块概览
module: vgm-intake
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 接单侧 VGM Intake 模块概览

接单侧 VGM 是 BL 接单域上的独立业务门面，入口为 `VgmInfoController` 的 `/api/v1/vgm/**`，编排为 `VgmInfoManagerImpl`，配置由 `VgmConfigResolver` 解析，详情快照使用 Mongo `vgm_detail`。

它支持从 BL 创建、前置校验、列表、详情、保存、接单、提交、关闭、备注和操作历史。主数据为 `vgm_info`。该模块可以投影 BL 联合提交结果，但不是 BL 详情字段，也不是通道侧 `biz_vgm_record`。

## 位置、职责与非目标

`VgmInfoController` 只提供接单侧门面；`VgmInfoManagerImpl` 负责租户上下文、来源 BL 校验、能力解析、附件/异常投影、Mongo 详情、操作日志和向 Bill/VGM Input 的调用。`VgmDetailServiceImpl` 保存 `vgm_detail`，`VgmConfigResolverImpl` 组合解析联合与独立能力，`VgmCombinedProjectionManager` 只投影联合提交的成功事实。

非目标包括：不在 `vgm_info` 复制通道侧执行状态机；不把 BL 表单作为 VGM 保存后的实时真源；不允许联合投影记录执行独立 VGM 的接单、保存、关闭和备注操作。

## 为什么采用当前设计

独立 VGM 是人工可编辑的业务单，联合 VGM 是 BL+VGM 已提交事实，两者权限和产生时机不同。把当前态放在 `vgm_info`、复杂表单放在 Mongo，可以支持列表筛选和表单演进；代价是 MySQL/Mongo 不具备代码可证明的原子事务，需要锁、稳定 taskNo、条件状态和操作快照帮助收敛。

## 风险、差异与未知项

- `createFromBill` 用 `lock:vgm:createFromBill:{tenant}:{blId}` 降低并发重复，但历史幂等仍依赖重复查询/业务约束。
- 联合投影用 `sourceTaskNo` 查重；源码注释明确其用于回调重放和强制监听重试幂等。
- 设计文档描述产品目标；本文只将 Controller、Manager、实体和当前配置解析器能证明的行为列为现状。
- 当前代码无法确认生产 `VGM_SUBMISSION:*` 值、官网账号可用性、外部 SLA 与所有租户的联合能力。

## 面试深挖与来源

可追问“为什么独立单与事实投影不能共用操作权限”“分布式锁与 sourceTaskNo 分别解决什么问题”“MySQL 当前态与 Mongo 详情如何补偿”。来源：`VgmInfoController`、`VgmInfoManagerImpl`、`VgmDetailServiceImpl`、`VgmConfigResolverImpl`、`VgmCombinedProjectionManager`、`VgmInfo`、`sql/vgm/` 与 VGM 设计文档。
