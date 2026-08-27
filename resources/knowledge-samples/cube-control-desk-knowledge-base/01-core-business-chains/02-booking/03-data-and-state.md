---
title: Booking 数据模型与状态
module: booking
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Booking 数据模型与状态

## 证据合同与实现核验

数据分层：任务壳 `biz_task`/`biz_customer_task`/Ext；订舱当前态 `biz_advance_booking`/Ext/Container；账号 `biz_booking_account*`；船司能力 `biz_booking_carrier_config` 与 `booking_configuration*`。`BizAdvanceBooking` 是当前页面/流程真源，历史日志不能替代它。`WAITING_RUN` 表示等待外部执行，`SUCCESS_RUN` 表示回执成功。

状态写入由 Provider/manager 回调和 `BizAdvanceBookingServiceImpl` 完成，任务状态由 task service 完成；跨表并非天然单事务，事务边界需看具体方法。重复回调、任务找不到、外部成功而本地更新失败是关键风险。代码/文档差异：不能将订舱成功描述成放舱成功。测试证据为 Controller 单测；第三方幂等、重试与锁策略当前代码无法确认。源码列表：model entity/enum、provider、manager、service、state-machine actions、SQL；最后验证日期 2026-08-26。

## 核心数据

| 数据 | 用途 |
|---|---|
| `biz_task` | 通用任务壳 |
| `biz_customer_task` / `biz_customer_task_ext` | 租户任务及扩展上下文 |
| `biz_advance_booking` | 订舱当前主记录 |
| `biz_advance_booking_ext` | 船司返回、结果与扩展信息 |
| `biz_advance_booking_container` | 订舱箱信息 |
| `biz_booking_account*` | 订舱账号及其配置 |
| `biz_booking_carrier_config` / `booking_configuration*` | 船司能力与字段配置 |

## 任务状态

状态机实现位于 `StateMachineBuilderBookingConfig`，任务状态和事件来自 `TaskBizStatusEnum`、`TaskBizStatusEvent`。具体状态迁移必须按目标机器（AUTO、SINGLE、STANDARD）和 action 类核验；不能用一个状态表替代三套机器。

## 当前态与历史

`BizAdvanceBooking` 是当前订舱主记录，扩展表承载更多执行结果。Booking 回调成功不等于 Release 成功；Release 的当前状态和历史由 Release 链路另行维护。
