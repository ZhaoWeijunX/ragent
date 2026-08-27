---
title: Booking 订舱模块概览
module: booking
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Booking 订舱模块概览

## 证据合同与实现核验

目的/读者：帮助后端理解订舱任务如何组装、执行、回调；非目标：不把 Release 成功或第三方执行器实现归入 Booking。入口包括 `BizCommandBookingController`、`BizCommandAdvanceBookingController`、`BizCommandAdvanceBookingV2Controller`，OpenAPI 为 `ICommandBookingOpenApi`/`CommandBookingOpenApiProvider`。

已核验链：Web/OpenAPI/SHIPPING 组装 `BookingParam` → `BizCommandBookingManager`/Provider 创建 `BizCustomerTask` → `StateMachineBuilderBookingConfig` 与 booking actions → 外部回执 `bookingCallback` → `BizAdvanceBooking`/Ext 更新 → 按配置决定是否创建 `RELEASE_SPACE`。核心数据为 `biz_task`、`biz_customer_task`、`biz_advance_booking`、账号与船司配置表。

代码/文档差异：`SUCCESS_RUN` 仅代表订舱回执成功，不代表放舱成功；当前 checkout 没有稳定独立 Booking api-test 主链。未知项：第三方执行器 SLA、线上状态机重放策略当前代码无法确认。源码列表为上述 controller/provider/manager/state-machine、`BizAdvanceBookingServiceImpl`、model entity、配置 SQL；最后验证日期 2026-08-26。

Booking 不是单一 CRUD，而是任务、状态机、参数清洗、第三方执行、回调和配置的组合编排。入口分布在 Web Controller、OpenAPI Provider、SHIPPING 去订舱协作以及第三方 Job。

## 关键代码

- `BizCommandBookingController`、`BizCommandAdvanceBookingController`、`BizCommandAdvanceBookingV2Controller`：Web 入口。
- `ICommandBookingOpenApi` / `CommandBookingOpenApiProvider`：内部 OpenAPI 与回调。
- `BizCommandBookingManager`：自动、单票、标准等订舱编排。
- `StateMachineBuilderBookingConfig`：订舱状态机。
- `BizAdvanceBookingServiceImpl`、`BizAdvanceBookingExtServiceImpl`：订舱主记录和扩展数据。
- `Mh8BookingCallbackJob`：MH8 回调采集与转发。

订舱成功仅表示执行回执被消费；是否创建放舱监听由配置和回调处理决定，不能把 Booking 与 Release 合并理解。
