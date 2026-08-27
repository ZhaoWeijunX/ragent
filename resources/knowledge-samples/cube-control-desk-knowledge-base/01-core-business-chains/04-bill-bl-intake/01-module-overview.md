---
title: BILL/BL 接单模块概览
module: bill-bl-intake
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# BILL/BL 接单模块概览

## 证据合同与实现核验

目的/读者：面向维护 BL 接单门面、工单、详情、异常和回调的后端；非目标是 Bill Input 通道主链。入口位于 `BLEntrustedInfoController`、`BLCallbackController`、`BlOpenApiController`、文件/字段来源/账号映射 controller；编排核心为 `BLEntrustedInfoManagerImpl`。

已核验代码链：共享 `/api/v1/workOrder/v2/create` 经 `workOrderType=BILL` 分派到 `AbstractBillOrderCreate`，写独立 `bl_work_order`、`bl_entrusted_info` 与 Mongo 详情；BL manager 处理接单、保存、关闭、强制监听、异常修复、操作日志和 VGM 投影；回调进入 `BillOfLadingCallbackManager`。可调用 Bill Input，但不拥有其 `biz_bill_record`、规则、文件监听和状态机。

代码/文档差异：BILL 不是 invoice/OCR 的同义词，主表不应写 `entrusted_work_order`。数据证据为 `BLEntrustedInfo`、`BLWorkOrder`、`bl_*` SQL、Mongo detail/source documents。测试包括 `BLEntrustedInfoManagerImplCloseTest`、`...ForceMonitorTest`、`BillOfLadingCallbackManager*Test`；未知项：外部 Bill 服务 SLA、生产回调重放规则当前代码无法确认。源码列表同上述类与 `sql/bill`；最后验证日期 2026-08-26。

BILL/BL Intake 是提单接单门面和工单域，主数据独立于 SHIPPING 与 Bill Input。代码入口在 `app/.../entrusted/bl/controller`，业务编排在 `biz/.../entrusted/bl/manager`，主数据表以 `bl_*` 为主，详情和字段来源还使用 Mongo 文档。

关键入口包括 `BLEntrustedInfoController`（列表、详情、保存、提交、异常修复）、`BLCallbackController`（统一回调路由）、`BlOpenApiController`（查询/分享接口）、`BLEntrustedInfoFileController`、`BLFieldSourceController` 与 `BLMailAccountMappingController`。

该域可以调用 Bill Input 完成提单提交，但不拥有 Bill Input 的通道规则、任务状态机和文件监听；两者不能混用主表或状态。

## 运行时职责的细化

`BLEntrustedInfoManagerImpl` 是接单侧的业务边界，而不只是 Controller 的转发层。以关闭为例，`close` 在同一 Spring 事务中先校验 `BLEntrustedInfoStatusEnum.getCloseAllowedStatusList()`，再更新当前状态、解决缺失异常、重算异常投影；若有提单号还同步重复单组，若为拆单成员还先刷新未关闭成员的合单订舱号再同步拆单组。这样设计是为了让单据关闭不会留下“已关闭但仍显示打开异常”的派生数据。

提交则刻意把外部系统边界收敛到 `BillClient`：`submitPreview` 发送 `processType=DRAFT`，`submitCarrier` 发送 `processType=SUBMIT`，并分别写 `SUBMITTING_PREVIEW`、`SUBMITTING_CARRIER`。两条路径都从 Mongo detail 克隆 `formData`、补拆单字段、解析委托客户，再由 client 按租户/客户配置寻址；这避免 Controller 或其它调用方绕过客户级配置直接发 HTTP。

`forceMonitor` 是另一条语义不同的路径：它要求当前状态可监听、合单信息和拆单主单就绪，调用远端成功后直接落到 `SUBMITTED_CARRIER`，并保存回传的 `taskNo`。因此它不能被当作带截图和提交快照的 `submitCarrier` 替代品；Manager 源码也对此保留了说明。

## 边界与风险

Manager 上的本地状态更新带 `@Transactional(rollbackFor = Exception.class)`，但 `BillClient` 调用发生在该事务之内：若远端已受理而后续本地写库失败，代码无法提供跨服务原子提交。排障和重放前应先用 `taskNo`、操作快照和远端查询确认是否已受理，避免重复提交。回调幂等键、远端 SLA 与签名校验细节在当前检索代码中无法确认，不能据此假定“重试安全”。
