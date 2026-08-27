---
title: Release 放舱模块概览
module: release
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Release 放舱模块概览

## 证据合同与实现核验

目的/读者：面向需要维护订舱成功后放舱监听的后端；非目标是不描述 Booking 成功判定。Release 由 `CommandBookingOpenApiProvider#createReleaseMonitoringTask/createReleaseSpaceTask` 派生，统一回调为 `releaseSpaceCallback`；监听入口分 API、Website、Email、ASTA。

真实代码：`ApiReleaseJob → ApiReleaseStrategyImpl`、`WebsiteReleaseJob/WebsiteReleaseResultObtainJob → WebsiteReleaseStrategyImpl`、`EmailReleaseJob → EmailReleaseStrategyImpl`、`AstaEmailReleaseJob → AstaEmailReleaseStrategyImpl`，结果归一为 `ReleaseResultDto` 写 `BizAdvanceBooking`/Ext 与 `BizReleaseResultRecord`。配置读取 `TenantConfigValue.releaseConfig.needCreateReleaseTask`、`CarrierConfigTypeEnum.RELEASE`。代码/文档差异：历史 Push Retry 与 `BusinessRetryJob` 的 MAIL_RELEASE_FAILED 补偿是不同机制；未知项：供应商状态码完整表、SLA 和生产告警当前代码无法确认。源码/测试列表为上述类、`EmailReleaseJobTest`/`ApiReleaseJobTest`/`WebsiteReleaseJobTest`；最后验证日期 2026-08-26。

Release 由订舱成功派生，负责创建 `RELEASE_SPACE` 任务、从 API/Website/Email/ASTA 等来源监听船司结果，并通过统一 `releaseSpaceCallback` 写入订舱当前态、扩展结果和放舱历史。

## 关键代码

- 创建与统一回调：`CommandBookingOpenApiProvider.createReleaseMonitoringTask`、`createReleaseSpaceTask`、`releaseSpaceCallback`。
- 状态机：`StateMachineBuilderReleaseSpaceConfig`。
- 监听 Job：`ApiReleaseJob`、`WebsiteReleaseJob`、`EmailReleaseJob`、`AstaEmailReleaseJob`。
- 统一策略基类：`AbstractReleaseStrategy` 及 API/Website/Email/ASTA 实现。
- 当前数据：`BizAdvanceBooking`、`BizAdvanceBookingExt`；历史：`BizReleaseResultRecord`。

Job/Strategy 负责发现并归一化结果；正式写库入口是 `releaseSpaceCallback`。因此订舱成功不等于放舱成功，历史记录也不替代当前态。
