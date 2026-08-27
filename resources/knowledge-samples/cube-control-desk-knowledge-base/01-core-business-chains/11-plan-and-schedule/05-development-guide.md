---
module: plan-and-schedule
title: Plan 与 Schedule 开发指南
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Plan 与 Schedule 开发指南

Plan 改动先梳理五类实体及外键，再检查 create/modify/delete 的级联顺序、Mapper XML 聚合查询、导出填充账号/箱信息和 schedule task 停止逻辑。新增监控字段需同步 DTO、转换器、页面 VO、查询条件、Job 判定和通知。时间逻辑必须覆盖开始日前、窗口内、结束日结束时间后、结束日之后及 disabled。

并发上，超时关闭使用 cid+monitorId Redis 锁并二次查时间；普通 Job 仍可能与关闭 Job 竞争，改状态时必须保留条件更新和停止 Job 语义。删除计划前 Service 会检查是否存在订放舱数据，不能绕过保护直接删父记录。

Schedule 变更要保留 area 映射、每 50 条 batch、空列表和空采集列表的差异；清表重建应维持事务，不要在事务外增加读写。回归覆盖账号缺失、空计划、复制、跨租户 ID、并发超时和船期大批量刷新。面试深挖：为何二次校验仍需要锁？为什么计划状态和 Booking 状态分离？清表重建的原子性风险是什么？

源清单：Plan services/Job/helper/entity/Mapper XML、Schedule service/mapper/controller。

## 1. 修改清单

Plan 字段改动需同步 DTO、VO、转换器、Mapper XML、实体级联、导出、Job 判定和通知消息。时间逻辑至少覆盖开始日前、窗口内、结束日窗口后、结束日之后及 disabled；复制要重建子记录主键。删除不可绕过“已有订放舱数据”保护。

## 2. 并发与事务要求

超时 Job 使用 cid+monitorId Redis 锁，并在锁内重新读取当前时间；普通 Job 仍可能并发，关闭方法应继续采用条件更新并停止对应 jobId。Schedule 清表重建应维持事务和每 50 条批量边界，不能把单次刷新拆成事务外读写而扩大半成品窗口。

## 3. 回归与面试

回归覆盖账号缺失、空计划、复制、跨租户 ID、并发超时、通知失败、清表批量失败、区域无采集项和筛选 limit 100。面试可追问为何二次时间校验仍需锁、为何计划状态与 Booking 状态分离、清表重建如何保证原子性，以及 `sendOpenNotification` 为什么先发消息再条件更新。当前未执行真实 XXL 或外部 Booking 验证。

## 4. 证据要求

提交计划相关改动时应附 Controller 路由、Service 方法、Mapper/XML、Job 参数和测试结果；若只完成静态检查，明确标注外部调度与供应商调用未验证，以区分代码事实、运行态观察和设计推断。
