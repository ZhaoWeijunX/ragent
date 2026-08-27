---
title: Booking API 与跨模块协作
module: booking
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Booking API 与跨模块协作

## 证据合同与实现核验

Web API 由 `BizCommandBookingController`、AdvanceBooking/V2 controller 暴露；内部契约为 `ICommandBookingOpenApi`，实现为 `CommandBookingOpenApiProvider`。账号维护和配置查询在 `BookingAccountController`、`BookingCarrierConfigController`、`BookingConfigurationController`。输入最终汇聚到 `BookingParam`，回调由 `bookingCallback` 承接。

调用协作：SHIPPING/BL 等上游提供业务字段，Booking 读取 `BizBookingAccount` 与 carrier config，提交任务给外部执行器；回执写 Booking 当前态，并可调用 `createReleaseMonitoringTask` 创建 Release 任务。不要让上游直接改 `BizAdvanceBooking` 绕过状态机。代码/文档差异：旧 `sys_tenant_account` 不是当前账号真源。测试为 `BizCommandBookingControllerTest`；外部执行器契约、超时和鉴权当前代码无法确认。源码列表为 controller、OpenAPI、provider、manager、account/config services；最后验证日期 2026-08-26。

## OpenAPI 契约

`ICommandBookingOpenApi` 当前定义了：

- `/openApi/v1/command/booking/{taskCommand}`：创建订舱任务。
- `callback/collect|clean|confirm|booking/{taskCommand}`：阶段回调，其中 `booking` 对应 `bookingCallback`。
- `callback/releaseBooking`：放舱阶段订舱信息回写。
- `/openApi/v1/command/releaseSpace/{taskCommand}` 与 `callback/releaseSpace/{taskCommand}`：放舱任务及回调。
- `/openApi/v2/task/booking`、`bookingQuery`：V2 任务接口。

## 协作边界

SHIPPING 通过去订舱动作进入 Booking；Booking 成功后按租户配置和能力判断是否创建 Release 任务；MH8 Job 将解析结果转为 `BookingParam` 调用统一 `bookingCallback`。接口名来自当前 API 注解，具体鉴权和参数校验需继续追 Provider/Controller 实现。
