---
module: plan-and-schedule
title: Plan 与 Schedule 模块概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Plan 与 Schedule 模块概览

Plan 是按租户、船司、港口、箱型/数量和账号编排占舱计划监控的业务链；Schedule 当前实现是万海船期数据查询与批量刷新，两者都服务 Booking 前置或运营页面，但不等于订舱执行本身。Plan 页面入口为 `BookingPlanMonitorController`、`BookingPlanRecordController`，核心服务在 `biz.modular.plan.service`；Schedule 入口为 `ScheduleWhlController`，服务为 `BizScheduleWhlServiceImpl`。

Plan 拆成 Monitor、Record、RecordAccount、Container、ScheduleTask 五类实体，Job `BookingPlanMonitorJob` 按时间窗触发 `BookingPlanHelper.monitorSchedule`，超时 Job 每 10 分钟加 Redis 锁关闭监控并通知 Web。Schedule 查询按 area 且最多 100 条；保存先清空表再批量插入，事务保证单次刷新原子性。

本模块不拥有 Booking 当前态或外部订舱成功事实；计划资源计数与实际订舱数据协同由 helper/service 完成。未知项：生产 XXL/Quartz 调度配置、计划通知下游消费延迟和船期数据采集来源。

源清单：两类 Controller、Plan service/helper/Job、Schedule controller/service/mapper、Plan model entities。

## 1. 责任分层

Monitor 是时间范围和启停状态的计划容器，Record、RecordAccount、PlanContainer 描述订舱意图，ScheduleTask 描述调度窗口和 XXL jobId。`BookingPlanMonitorJob` 解析 monitorId/taskId 后调用 `BookingPlanHelper.monitorSchedule`；超时 Job 则通过 `RedisLockUtil` 关闭已到期 Monitor。ScheduleWhl 是独立船期缓存，不是 Booking 当前态。

## 2. 设计与风险

Plan 将计划定义与执行结果分离，使同一计划可多次调度并由 Booking 域记录真实结果；通知由 NoticeManager 和 QueueDefiner 负责，不应成为数据库状态的唯一依据。超时关闭和普通调度可能并发，状态更新与停 Job 必须保留条件。Schedule 保存采用清空后批量重建，读请求在刷新窗口内的语义需运行态补证。

## 3. 验证与未知

已核对 Plan 两个 Job、helper、实体和 Schedule service/mapper。当前代码无法确认生产 XXL cron、船期采集来源、通知延迟和外部 Booking 成功率。

## 4. 维护入口

分析计划问题应从 Controller 入参开始，沿 Monitor/Record Service 追到实体和 Mapper，再核对两个 Job 的时间判断；分析船期则从 `ScheduleWhlController` 独立追踪到 `BizScheduleWhlServiceImpl`。不要用计划页面的成功提示替代数据库和调度日志证据。
