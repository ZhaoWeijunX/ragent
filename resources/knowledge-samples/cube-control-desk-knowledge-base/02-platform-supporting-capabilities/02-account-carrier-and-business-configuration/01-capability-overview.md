---
title: 账号船司与业务配置能力概览
module: account-carrier-business-configuration
doc_type: capability-overview
last_verified: 2026-08-26
---

# 账号、船司与业务配置

该能力为 Booking/Bill Input/Release 提供租户、船司、账号、渠道和业务开关。入口是 `BookingAccountController`、`BookingCarrierConfigController`、`BookingConfigurationController`；模型包括 `BizBookingAccount`、`BizBookingAccountConfig`、`BizBookingAccountEmail`、`BizBookingCarrierConfig`，枚举包括 `CarrierConfigTypeEnum`、`BookingAccountChannelCodeEnum`。

```mermaid
flowchart LR
 U[租户+船司+渠道] --> A[BizBookingAccountService]
 U --> C[BizBookingCarrierConfigService]
 A --> P[Booking/Bill Provider]
 C --> P
 P --> E[任务/第三方执行]
```

配置是按 `cid + carrier + channel + config type` 解析，账号是当前 desk 真源；旧 `sys_tenant_account` 不应作为回退。能力只提供配置，不拥有 Booking 或 Bill Input 状态机。证据合同：测试 `BookingAccountServiceTest`、`BookingCarrierConfigServiceTest`；SQL/Mapper 及 entity 为本节列出类。代码/文档差异：复用渠道枚举不证明每个渠道都受支持；未知项：密钥托管、线上刷新策略、配置发布审批当前代码无法确认。最后验证日期 2026-08-26。

## 解析机制与深挖

调用方先确定可信 cid、carrier、channel，再由账号 service 和 carrier config service 查询主表及附属 Config/Email/User；`CarrierConfigTypeEnum` 区分 BOOKING、RELEASE、BILL_INPUT 等能力。该层只提供“是否可调用及参数”，不拥有业务状态机。并发编辑、账号禁用后在途任务、旧缓存覆盖新配置的处理方式当前代码无法确认。面试可追问为什么 desk 账号模型是真源、为什么枚举不等于渠道支持、如何以版本和 fail-closed 防止旧配置驱动生产任务。
