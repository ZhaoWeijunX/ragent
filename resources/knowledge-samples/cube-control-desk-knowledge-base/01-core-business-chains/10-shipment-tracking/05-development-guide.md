---
module: shipment-tracking
title: Shipment Tracking 开发指南
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Shipment Tracking 开发指南

BL 事件改动沿 Controller→DTO 校验→`BLTrackingEventServiceImpl`→Mapper/实体；保留 `@Transactional(rollbackFor=Exception.class)`、未来 5 分钟边界、事件/退出原因互斥和稳定排序。若改计算，必须同时看 `getPendingSessionCalcList`、session 增量查询和 markAsCalculated，避免重复计算或跳过最大 eventId。

Trace 改动沿 Command→Manager→OpenAPI/dispatch→state machine→mapping/notification，绝不能把供应商回执写进 BL 埋点表。新增事件类型需同步枚举、参数校验、描述构造和前端展示；新增节点需同步映射及失败/未知节点处理。

回归测试覆盖非法用户、mblId、未来时间、未知状态、EXIT 原因、重复 session、分页排序、增量窗口和跨租户 ID。当前未确认现有 API-test 能覆盖外部 Trace 供应商。面试深挖：为什么用 eventId 作为增量游标？为什么事件记录与业务状态解耦？为什么状态机比 Controller 直接调用供应商更适合异步重试？答案应引用实际方法。

源清单：BLTrackingEventServiceImpl、Mapper/XML、Trace state machine/dispatch/mapping。

## 1. 修改面与实现顺序

新增 BL 事件先改 DTO/枚举和 `validateExitReason`，再核对 `reportEvent` 的主表投影、Mapper/XML 与展示描述；不能只改前端。若改计算，必须同步检查 `getPendingSessionCalcList`、`listSessionEvents` 和 `markAsCalculated` 的游标边界与条件更新。若改 Trace 节点，则同时更新状态机动作、节点映射、协议转换和通知结果。

## 2. 保持的工程约束

BL 写入及标记保留 `rollbackFor=Exception.class`；分页维持 eventTime/id 排序；用户批量查询失败保持 accountId 回退。新增幂等时应基于 session 与事件语义设计，不能按 mblId 粗暴去重。Trace 修改不能把供应商回执写入 `bl_tracking_event`，也不能在 Controller 直接推进状态机。

## 3. 回归用例

覆盖空 DTO、未登录、非法 mblId、未来超过五分钟、未知事件/状态、EXIT 缺少或带错原因、非 EXIT 携带原因、用户查询失败、同一时间排序、游标窗口边界、重复回执和未知供应商节点。当前仓库无法确认外部 Trace 供应商能否在本地测试稳定模拟，应记录为验证缺口。

## 4. 面试深挖与依据

可追问为何服务从 BL 主表投影 cid 而不信任客户端、为何 eventId 是增量游标、事务为何不能覆盖外部调用，以及状态机如何避免 Controller 变成协议适配层。依据分别是 `reportEvent`、Mapper/XML、`TraceOpenApiService` 和 state-machine actions。
