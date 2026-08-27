---
module: plan-and-schedule
title: Plan 与 Schedule 排障与验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Plan 与 Schedule 排障与验证

计划不执行：核对 XXl 参数中的 monitorId/taskId、两条记录是否存在、monitor 日期、task 时间窗口、enabled/status、jobId 和 Redis 锁；再看 `BookingPlanHelper.monitorSchedule`、账号选择、资源计数和通知日志。提前结束：查 `maxEndDateTime`、结束日时间判断及 `closeMonitorByIdAndStopJob`。页面状态不刷新：查 NoticeManager 与 websocket QueueDefiner，不能据此判断数据库未更新。

计划删除/修改异常：检查是否存在订放舱数据、箱明细级联、账号逻辑删除和事务回滚。船期为空：区分 `/list` 无结果（area/模糊条件/limit 100）与 `/save` 空输入直接返回；保存后丢数据要核对清表事务、批量失败和区域映射。

静态定位可用 `rg -n "bookingPlanMonitorJob|bookingPlanMonitorTimeoutCloseJob|/api/v1/plan|/api/v1/schedule/whl"`；本次未运行真实 XXL、Booking 外部调用或 API-test，不能宣称调度运行验证通过。未知项：生产 cron、队列积压、船期采集来源与数据库 DDL。源清单：两 Job、Plan controllers/services、Schedule controller/service。

## 1. 故障矩阵

| 症状 | 首查证据 | 常见边界 |
|---|---|---|
| Job 不执行 | XXL 参数 monitorId/taskId、Monitor/Task 是否存在、日期和时间窗 | 缺数据只跳过，不代表任务成功 |
| 到期未关闭 | maxEndDateTime、Redis 锁、closeMonitorByIdAndStopJob 返回值 | 通知失败不一定阻止关闭 |
| 页面状态不刷新 | 数据库 status、NoticeManager、QueueDefiner | websocket 失败不等于库未更新 |
| 船期为空 | area、模糊条件、limit 100、采集列表 | 空采集与无结果是两种语义 |
| 删除失败 | 是否存在订放舱数据、级联 Service、事务日志 | 不能直接删父记录 |

## 2. 验证证据

静态验证按 Controller→Service→Mapper/XML→Job/helper 检查；运行验证需同时记录 XXL 执行参数、Redis 锁、数据库状态、通知日志和批量写入结果。本次未运行真实 XXL、外部 Booking 或 API-test，生产 cron、队列积压、采集来源和 DDL 仍属当前代码无法确认的信息。

## 3. 修复边界

计划窗口问题优先修 `skipCurrentSchedule`/关闭 Service；页面刷新问题检查通知链；船期问题检查 Schedule 服务和采集输入，不要修改 Booking 状态机来掩盖缓存缺失。
