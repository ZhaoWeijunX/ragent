---
module: shipment-tracking
title: Shipment Tracking 端到端流程
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Shipment Tracking 端到端流程

```mermaid
flowchart LR
 A[BL页面] --> B[/bl/tracking/report]
 B --> C[BLTrackingEventService]
 C --> D[校验登录/时间/状态]
 D --> E[(bl_tracking_event)]
 E --> F[待计算session查询]
 G[Trace Command] --> H[Trace Manager/OpenAPI]
 H --> I[dispatch与状态机]
 I --> J[供应商/Container Input]
 J --> K[回执、节点映射、推送]
```

BL 上报是同步写事件；`listByMblId` 查询时批量把 accountId 转为用户名称并构造描述，`listCalcEventsByMbl` 按 account/session/time/id 提供计算输入，`markAsCalculated` 按 session 和最大事件 ID 标记已计算。Trace 链路则由命令入口创建/推进任务，dispatch 负责执行，状态机动作处理成功/失败/回调，映射组件把供应商节点转换为平台节点。

不能从 BL report 推断已触发 Trace 供应商查询，也不能从 Trace 成功推断所有页面事件已收集。当前仓库静态可确认入口和 BL 事务实现；外部追踪供应商的完整时序需运行态日志补证。源清单：BL 服务、Mapper、Trace Manager/OpenAPI、state machine config/actions、dispatch。

## 1. BL 事件时序细节

上报要求登录用户、用户 ID、mblId、eventTime、activeStatus 存在，并拒绝超过当前时间五分钟的未来时间。事件类型由 `TrackingEventTypeEnum.getByCode` 解码；`EXIT` 必须匹配 `ExitReasonEnum`，其它类型不得携带退出原因。服务按 mblId 查 `BLEntrustedInfo`，从数据库对象取得 cid、提单号和船司，再记录 accountId、sessionId，并初始化 `calcStatus=0`。

## 2. 计算与展示的分叉

展示查询 `listByMblId` 仅按提单分页，批量把数字 accountId 解析为用户名称，查询失败回退原 accountId。计算查询先取得待处理 session，再按 cid、mblId、accountId、sessionId 与 `lastCalcEventId/maxEventId` 拉取窗口，成功后以最大事件 ID 批量标记。

## 3. Trace 端到端边界

Trace Command 创建或推进任务，Manager 调用 `TraceOpenApiService`/dispatch，状态机动作处理执行、失败和回执，映射组件把供应商节点转换为平台节点。BL 上报没有代码证据表明会自动触发 Trace；外部回执重试和原始报文留存需运行态补证。

## 4. 事务与故障点

BL report 和 `markAsCalculated` 使用 `@Transactional(rollbackFor=Exception.class)`，保证单库写入/批量标记的异常回滚。Trace 是跨外部系统流程，超时、403、未知节点和重复回执必须由 dispatch/状态机分别处理，不能把异常映射成成功。
