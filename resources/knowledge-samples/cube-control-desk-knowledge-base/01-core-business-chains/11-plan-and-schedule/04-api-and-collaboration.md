---
module: plan-and-schedule
title: Plan 与 Schedule API 与协同
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Plan 与 Schedule API 与协同

Monitor 基路径 `/api/v1/plan/monitor`，已确认 page、queryPlanMonitorById、create、copy、modify、stop、start 等入口；Record Controller 提供计划列表/详情/保存、状态、删除和导出等操作，实际方法以源码为准。Schedule 基路径 `/api/v1/schedule/whl`：`POST /list` 查询，`POST /save` 批量刷新；save 标注 `@Login(false)`，不能据此推断无租户边界，需继续检查服务/调用网关。

Plan Service 与 `BookingPlanHelper` 协同账号选择、箱量资源统计、通知和实际订舱；`BookingPlanMonitorJob` 通过 NoticeManager 发送开始/结束网站消息，并经 QueueDefiner 推送 websocket 刷新。外部 Booking 成功/失败由 Booking 域负责，计划仅提供意图和监控调度。

调用方改动须同步 DTO/VO、转换器、Mapper XML、Job 参数格式（monitorId/taskId）和通知消息 key。源码无法确认前端是否限制所有管理 API、XXL 参数注入来源和 Schedule save 的采集端身份。

源清单：Monitor/Record/Schedule controllers、Plan services/helper/manager、Jobs、NoticeManager/QueueDefiner、DTO/VO。

## 1. API 与调用方

Monitor API 覆盖 page、detail、create、copy、modify、stop、start；Record API 负责计划列表、保存、状态、删除和导出。ScheduleWhl 暴露 `/api/v1/schedule/whl/list` 与 `/save`。DTO/VO、转换器、Mapper XML 和 Job 参数格式构成一个协同契约，新增字段不能只改 Controller。

## 2. 安全与协同边界

Schedule save 标注 `@Login(false)`，仅表示该入口的登录注解配置，不能推断无租户授权；需结合调用网关和服务层确认。NoticeManager 发站内消息，QueueDefiner 发 websocket 刷新，二者是通知通道，不是 Plan 状态真源。BookingPlanHelper 负责把计划意图交给下游，Booking 当前态仍归 Booking 域。

## 3. 失败语义

缺 monitor/task 时 Job 跳过；越过日期/时间窗口或禁用时不执行；结束时停止 Job。通知异常与状态关闭可能分离，故障排查必须分别查数据库、XXL 日志、通知和 websocket。XXL 参数注入来源、网关鉴权和采集端身份当前无法确认。

## 4. 变更影响

新增计划字段至少影响创建、复制、详情、导出和 Job 参数；新增 Schedule 字段还要核对 area 映射及批量保存。跨域调用的稳定关联应使用 monitorId/taskId 或明确的 Booking 业务键，不能依赖页面文案。
