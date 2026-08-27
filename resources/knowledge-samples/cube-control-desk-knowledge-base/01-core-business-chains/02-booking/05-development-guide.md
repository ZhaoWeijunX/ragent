---
title: Booking 开发定位指南
module: booking
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Booking 开发定位指南

## 证据合同与实现核验

改动顺序：先读 Controller/OpenAPI → `BizCommandBookingManager`/Provider → `StateMachineBuilderBookingConfig` 与 actions → task/advance service → entity/enum/SQL；确认账号使用 `BizBookingAccountService`。不要在 Booking 中实现 Release 状态，不要直接更新当前态绕过回调。新增状态需同步枚举、状态机 action、回调、列表和测试。

必须覆盖 Web/OpenAPI、V2、重复回调、外部失败、任务不存在、配置关闭 Release、账号缺失和跨租户 ID。现有测试 `BizCommandBookingControllerTest` 只能证明局部 Controller；稳定主链 api-test 当前代码无法确认。代码/文档差异及未知项按主链文件；源码列表为本节所有类与对应 Mapper/XML/SQL；最后验证日期 2026-08-26。

## 推荐顺序

先找 app Controller 或 `ICommandBookingOpenApi`，再追 `CommandBookingOpenApiProvider` / `BizCommandBookingManager`，之后查看状态机 config、action、core booking Service、model enum，最后看 Job 和 api-test。

## 变更检查

- 明确属于 AUTO、SINGLE、STANDARD 还是 V2。
- 检查 `BookingParam` 的清洗、校验、账号和船司配置来源。
- 回调修改要同时检查当前主记录、扩展表、任务状态和日志。
- 若会创建 Release 任务，继续核验 `needCreateReleaseTask` 与 `createReleaseSpaceTask`。
- 不要把 `BizBookingAccount` 回退为旧 `sys_tenant_account`。

现有 `doc/onboarding/booking-release-quick-start.md` 与 `doc/wiki/module-booking.md` 可用于导航；实际行为以当前代码为准。
