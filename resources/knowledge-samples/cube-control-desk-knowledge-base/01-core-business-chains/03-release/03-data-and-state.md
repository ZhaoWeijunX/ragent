---
title: Release 数据模型与状态
module: release
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Release 数据模型与状态

## 证据合同与实现核验

当前态位于 `biz_advance_booking`/`biz_advance_booking_ext`；来源监听表为 `biz_api_release_space`、`biz_website_release_space`、`biz_mail_release_space`、`biz_release_asta_data`；历史为 `biz_release_result_record`、`biz_release_log`、`biz_release_send_log`。`ReleaseResultDto` 是 API/Website/Email/ASTA 的统一边界。配置包括 `needCreateReleaseTask`、`supportPendingStage`、`supportPendingReviewStage`、`BizReleaseFillConfig`、`ExpMonitorTypeEnum`。

状态写入由统一 `releaseSpaceCallback` 承担，Job/strategy 只负责获取和解析；邮件重试写 `biz_business_retry_task`，由 `BusinessRetryJob` 调 `MailReleaseFailedRetryHandler`。风险是重复回调、监听游标推进与业务写入不一致、当前态和历史快照混读。代码/文档差异：pending 不是失败，历史记录不是当前真源。测试为四类 Job 单测；跨系统事务、幂等键和告警阈值当前代码无法确认。源码列表为 Jobs/strategies/Provider/callback、release entity/service、`sql/retry/biz_business_retry_task.sql`；最后验证日期 2026-08-26。

## 监听与结果数据

| 数据 | 用途 |
|---|---|
| `biz_api_release_space` | API 监听来源 |
| `biz_website_release_space` | Website 监听来源 |
| `biz_mail_release_space` | Email 监听来源 |
| `biz_release_asta_data` | ASTA 数据来源 |
| `biz_advance_booking` | 订舱/放舱当前状态主记录 |
| `biz_advance_booking_ext` | 当前放舱结果等扩展信息 |
| `biz_release_result_record` | 每次有效回调的历史快照 |
| `biz_release_log` / `biz_release_send_log` | 放舱处理和发送日志 |

## 语义边界

`pending` 表示船司审核阶段，不等于失败；`confirm` 是首次确认，`update` 是非首次更新，`cancel` 是取消。页面当前状态优先读取 `BizAdvanceBooking`/`BizAdvanceBookingExt`；`BizReleaseResultRecord` 用于历史展示。

## 配置

当前文档和代码可见配置包括 `TenantConfigValue.releaseConfig.needCreateReleaseTask`、`CarrierConfigTypeEnum.RELEASE`、`supportPendingStage`、`supportPendingReviewStage` 与 `RELEASE_SPACE_FILLED_{cid}` 脚本 key。具体生效条件仍以调用处为准。
