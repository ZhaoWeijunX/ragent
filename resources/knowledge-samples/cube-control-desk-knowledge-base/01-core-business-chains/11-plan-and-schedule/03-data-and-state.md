---
module: plan-and-schedule
title: Plan 与 Schedule 数据与状态
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Plan 与 Schedule 数据与状态

Plan 主对象 `BizAdvanceBookingPlanMonitor` 保存 cid、用户、日期范围、最大结束时间、启用/状态和通知标记；`BizAdvanceBookingPlanRecord` 保存单个计划；RecordAccount 保存可用订舱账号；PlanContainer 保存箱型/数量、港口和计划明细；ScheduleTask 保存开始/结束时刻、频率与调度 Job ID。创建、修改、删除由对应 Service 级联维护，Mapper XML 含聚合/资源查询。

代码中 Job 把 monitor `status=2` 作为开启通知后的状态，把 `status=3` 作为关闭筛选事实；`enabled=1` 表示禁用并跳过本次调度。不要混淆 status 与 enabled。结束日期/时间由 `maxEndDateTime` 和 task 时间窗口共同决定。超时 Job 按页查询未关闭且 maxEndDateTime 已到期记录，并在 Redis monitor 锁内二次校验。

`BizScheduleWhl` 是独立船期表，area 是账号区域投影；保存实现“清空 + 批量重建”，空输入直接记录错误返回，非空但某区域无采集项则参数异常。源码未确认表 DDL、保留历史版本和多实例清表期间读请求语义。

源清单：五个 Plan entity、`BizAdvanceBookingPlan*ServiceImpl`、两 Job、Schedule service/mapper/entity。

## 1. 状态与时间

Monitor 的 `status=2` 表示已发送开启通知/运行中，`status=3` 是关闭筛选事实；`enabled=1` 表示禁用，二者不能互换。日期由 beginDate/endDate 控制，时间窗口由 ScheduleTask beginTime/endTime 控制，maxEndDateTime 是超时关闭的最终边界。普通 Job 结束日结束时间后关闭，超时 Job 会再做一次 LocalDateTime 校验。

## 2. 实体关系与事务

Monitor 聚合多个 Record；RecordAccount 绑定订舱账号；PlanContainer 保存箱型数量与港口；ScheduleTask 保存频率和 jobId。创建/修改/删除由 Service 按级联顺序维护，删除前检查是否已有订放舱数据。ScheduleWhl 的 area 是账号区域投影，保存逻辑是清表后批量重建，事务能保护单次写入，但跨请求读语义无法从代码确认。

## 3. 一致性问题矩阵

| 问题 | 证据 | 风险 |
|---|---|---|
| 计划仍执行 | enabled、status、Job 参数 | 普通 Job 与关闭 Job 竞争 |
| 结束未关闭 | maxEndDateTime、Redis 锁、条件更新 | 仅看页面状态不充分 |
| 船期缺失 | area、筛选 limit 100、清表批次日志 | 可能是采集为空而非丢库 |

未知：历史版本保留、DDL 唯一约束和批量失败后的恢复策略。

## 4. 状态迁移检查

状态迁移应关注 `sendOpenStatus/sendCloseStatus` 与 status 的组合，而非只看单列。开启通知成功后才条件更新为运行状态；关闭流程可能在通知异常时仍落库关闭，因此排障需分别判断业务状态和用户通知状态。
