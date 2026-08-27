---
module: shipment-tracking
title: Shipment Tracking API 与协同
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Shipment Tracking API 与协同

BL API 基路径 `/api/v1/bl/tracking`：`POST /report` 接收 `TrackingEventDTO`，`GET /events?mblId&current&size` 返回 `TrackingEventVO` 分页。Controller 标注 `@ApiPermission(false)`，但 report 仍要求 `LoginContext` 用户和 userId；服务通过 mblId 读取 BL 记录并写入其 cid/carrier/blNo。调用方通常是 BL 详情页，不能把该 API 当外部供应商回调。

Trace 侧入口是 `BizCommandTraceController`/`BizCommandTraceManager`，对外协同经 `TraceOpenApiService`，任务由 dispatch 和状态机动作处理，节点转换依赖 `TraceNodeIdMapping`、`TraceIframeKeyMapping` 等映射。跨模块排查必须分别提供 mblId/sessionId/eventId 与 trace taskId/taskNo/节点 key。

接口返回 `ResponseData`；事件时间是 epoch millis，eventType 使用字符串编码。仓库无法确认网关是否额外限流、Trace OpenAPI 的认证及供应商错误映射。源清单：BL Controller/DTO/VO/Service、Trace Controller/Manager/OpenAPI/映射类。

## 1. 接口契约与责任

Controller 的 report API 是页面埋点入口，不是供应商 webhook；它依赖登录上下文，服务层从 mblId 对应的 BL 记录取得 cid、blNo、carrier。查询接口返回分页 VO，并在服务中生成事件描述。`@ApiPermission(false)` 只表达该注解层面的权限配置，不能代替 mblId 资源授权，当前代码无法确认网关是否另有鉴权。

## 2. 协同数据

BL 调用方应携带 mblId、sessionId、eventType、eventTime、activeStatus/currentStatus；排障至少记录 eventId、accountId 和响应结果。Trace 协同则使用 taskId/taskNo、carrier、账号、节点 key 和回执原文。两套诊断 ID 必须分开，避免用 sessionId 去查供应商任务。

## 3. 兼容与异常

非法类型、非法状态、无提单和未来时间由参数异常拒绝；用户名批量查询失败时展示回退 accountId。Trace 的 403、超时、未知节点由 dispatch/状态机处理，API 层不应吞掉原始错误。网关限流、Trace OpenAPI 认证和错误码映射未由当前仓库确认。

## 4. 文档—代码差异

文档中的“BL 埋点”来自 `BLTrackingEventController`/`BLTrackingEventServiceImpl`；文档中的“Trace 回执”来自 `TraceOpenApiService` 与 dispatch/state machine。两者没有代码证据表明共享一个回调表或自动互触发，后续改动需同时更新本目录的接口契约和状态边界。
