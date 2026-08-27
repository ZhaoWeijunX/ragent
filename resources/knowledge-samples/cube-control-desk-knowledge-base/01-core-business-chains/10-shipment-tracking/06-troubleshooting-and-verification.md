---
module: shipment-tracking
title: Shipment Tracking 排障与验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Shipment Tracking 排障与验证

先分流：问题是 BL 事件未写/未展示，还是 Trace 任务未执行/节点未推送。BL 路径记录 mblId、sessionId、eventType、eventTime、登录 userId；查 `bl_tracking_event` 的 cid、calcStatus 和排序。失败时区分参数断言、BL 不存在、数据库事务异常、用户名批量查询失败（后者应回退 accountId）。

Trace 路径记录 taskId/taskNo、carrier、账号、节点 key 和回执原文，沿 Manager、dispatch、状态机动作、映射、通知日志查；未知节点或供应商 403 不应伪装为业务成功。增量计算检查 lastCalcEventId/maxEventId 是否单调，标记更新是否带 cid/session 条件。

可用 `rg -n "BLTrackingEvent|/bl/tracking|TraceOpenApiService|StateMachineBuilderTrace"` 做静态定位；本次未执行外部船司/追踪供应商调用或完整 API-test，因此验证结论限于源码和 DDL。未知项：生产告警阈值、事件保留期、供应商重试结果。源清单：BL service/controller/mapper、Trace chain classes。

## 1. 故障矩阵

| 症状 | 排查顺序 | 结论边界 |
|---|---|---|
| report 参数错误 | DTO、登录、eventType/currentStatus、EXIT 原因、eventTime | 不是数据库故障 |
| report 成功但列表为空 | mblId、分页、eventTime/id、实际 cid | 不能只看前端缓存 |
| calcStatus 不变化 | pending session、last/max eventId、Job 与 mark SQL | 不能当作上报失败 |
| 用户名未解析 | accountId 是否数字、QueryTenantTools 结果 | 事件仍可能已落库 |
| Trace 节点失败 | taskId/taskNo、dispatch、mapping、供应商原文 | 403/未知节点不得标成功 |

## 2. 验证步骤与证据

静态验证逐层检查 Controller 路由、Service 断言、实体字段、Mapper/XML 条件和调用方；运行验证还需记录数据库前后值、Job 参数、供应商响应和通知日志。当前未执行真实 XXL、供应商/API-test，不能宣称异步 Trace 链路通过。

## 3. 修复边界

BL 页面事件问题不应修改 Trace 状态机；供应商节点映射问题不应改 `BLTrackingEventServiceImpl`。若发现跨租户 mblId 可读写，应补服务层资源授权和回归测试，而不是只隐藏前端入口。
