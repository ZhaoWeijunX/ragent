---
module: shipment-tracking
title: Shipment Tracking 数据与状态
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Shipment Tracking 数据与状态

`BLTrackingEvent` 字段为 cid、mblId、blNo、accountId（实际写入登录用户 ID 字符串）、sessionId、eventType、eventTime、activeStatus、currentStatus、containerCount、carrierCode、exitReason、calcStatus、createTime。事件类型只有 `enter`、`exit`、`active_change`、`heartbeat`；EXIT 必须合法 ExitReason，其它事件不允许携带退出原因。服务把 calcStatus 初始化为 0。

BL 查询按事件时间和自增 id 排序；计算查询按 account/session 分组再按时间/id 排序，使用 lastCalcEventId/maxEventId 形成增量窗口，标记时在事务中批量更新。Trace 数据模型包括任务、推送消息、订阅记录和节点映射，不能用 BLTrackingEvent 字段替代。具体 Trace 状态枚举以状态机构建配置及动作代码为准，当前本稿不编造未读到的编码。

一致性上，BL 上报是单库事务，但没有从代码确认 mblId 与当前登录用户/租户的额外授权规则；服务通过 BL 查询得到 cid。未来时间限制降低客户端时钟异常影响，但不防止同一 session 重复上报。未知项：清理策略、事件去重约束和 Trace 供应商原始报文留存期限。

源清单：BLTrackingEventServiceImpl、实体/枚举、Mapper/XML、Trace state machine models。

## 1. 字段语义与状态机边界

`cid`、`mblId`、`blNo`、`carrierCode` 是从提单主数据投影的归属字段；`accountId/sessionId` 标识用户会话；`eventTime` 是客户端事件时间；`activeStatus/currentStatus/containerCount` 是事件快照；`calcStatus` 是统计处理标记，不是 BL 当前状态。事件类型当前为 enter、exit、active_change、heartbeat，退出原因仅对 exit 合法。

## 2. 增量一致性

分页采用 `eventTime ASC, id ASC`，同一时间仍有稳定顺序。计算按账号和 session 聚合，用 `lastCalcEventId` 与 `maxEventId` 形成增量窗口；批量标记带 cid、mblId、accountId、sessionId 条件。Service 未显示唯一键或幂等表，重复上报是当前限制。

## 3. Trace 数据隔离

Trace 任务、推送消息、订阅记录、节点映射和供应商回执具有自己的模型和状态机。节点状态不能通过 `currentStatus` 猜测，必须以 `StateMachineBuilderTrace` 及动作代码为准；关联应使用 taskId/taskNo 或明确业务键。

## 4. 数据问题矩阵

| 现象 | 首查证据 | 不能直接推断 |
|---|---|---|
| 顺序异常 | eventTime、id 双排序 | 不能直接认定数据库乱序 |
| calcStatus 为 0 | pending session、游标、Job 日志 | 不能推断上报失败 |
| 用户名为数字 | accountId 转换和用户查询 | 不能推断事件未落库 |
| Trace 节点未知 | mapping 与供应商原始节点 | 不能改写为成功 |
