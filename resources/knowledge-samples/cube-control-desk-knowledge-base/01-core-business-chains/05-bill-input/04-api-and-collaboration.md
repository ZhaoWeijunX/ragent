---
title: Bill Input API 与协作
module: bill-input
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# Bill Input API 与协作

## 契约和协作约定

Web `/api/v1/command/bill/*` 由 Controller 装配租户会话；`ICommandBillInputOpenApi`/Provider 暴露 submit、monitor、receipt、file receipt、identification receipt、field config。Provider 按 taskNo 找任务后交给 Manager；`BillClient` 是 BL 等调用方的适配器，不改变 Bill Input ownership。

配置从 `BizBookingCarrierConfigService#getBillInputConfig(cid, carrier, channel)` 读取，类型为 `CarrierConfigTypeEnum.BILL_INPUT`；账号由 `BizBookingAccountService` 按 cid/船司查 `BizBookingAccount`。当前规则代码确认官网 channel=1，其他渠道未知。回调应保留 taskNo、dataType、外部原始结果和 file key。

测试至少覆盖 TEMP、清洗失败、receipt 成功、DRAFT→COPY、AUDIT_FAIL；运维交接需关联 task、record、Mongo、schedule 和原始回执。证据：`ICommandBillInputOpenApi`、Provider、`BillClient`、Controller、`BookingConfigurationController`、bill-desk API 场景；最后核验 2026-08-26。

Web 接口包括 `/api/v1/command/bill/create`、`collect`、`callback/confirm`、`queryFieldConfig` 及地点/付款/公司/城市查询。内部 OpenAPI 包括 `/openApi/v1/internal/bill/submit`、`monitor`、`/openApi/v1/task/bill/check/receipt`、`receipt`、`file/receipt`、`identification/receipt`。

字段配置读取 `BizBookingCarrierConfigService#getBillInputConfig(cid, carrier, channel)`，当前规则分发只确认官网 channel=1；不要因为共享订舱 channel 枚举就推断支持全部订舱渠道。

BL Intake 通过 `BillClient` 调用提交/配置/监听能力；回调由 Bill Input Provider 接收后再由 BL callback 路由按 dataType 处理。
