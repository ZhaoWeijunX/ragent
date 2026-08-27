---
title: Release 常见排查与验证
module: release
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Release 常见排查与验证

## 证据合同与实现核验

排查顺序：先确认 Booking 回调是否满足创建 Release 条件，再查 `RELEASE_SPACE` task 和监听表，按来源追 Job/strategy，最后查 `releaseSpaceCallback`、`biz_advance_booking`/Ext 与历史记录。Email 失败要区分 ASTA、可重试异常和 `MAIL_RELEASE_FAILED` 业务重试。

验证证据：`ApiReleaseJobTest`、`WebsiteReleaseJobTest`、`EmailReleaseJobTest`、`AstaEmailReleaseJobTest`；当前代码未提供稳定独立 api-test 主链。风险是“有历史无当前态”、pending 被误判失败、游标跳过邮件、重复回调和重试重复处理。代码/文档差异、未知项按上述主链；源码列表为 Jobs/strategies/Provider/callback/retry/SQL；最后验证日期 2026-08-26。

## 没有创建放舱任务

回到 Booking 的 `processBookingRecordWhenBookingCallback`，检查 `needCreateReleaseTask`、租户配置、船司 RELEASE 能力与 `createReleaseSpaceTask`。不要从放舱监听表为空直接判断订舱失败。

## 有监听但状态未更新

确认对应 Job 是否扫描到期记录、Strategy 是否成功解析结果、是否调用 `releaseSpaceCallback`，再检查 `BizAdvanceBooking`、`BizAdvanceBookingExt` 和 `BizReleaseResultRecord`。

## 页面显示与历史不一致

先以 `BizAdvanceBooking`/`Ext` 的当前态为准，再检查历史快照是否追加；历史表不是当前状态真源。

## Email 失败

确认异常是否满足 `EmailReleaseJob` 的可重试条件、是否写入 `biz_business_retry_task`，再追 `BusinessRetryJob` 和 `MailReleaseFailedRetryHandler`。ASTA 不是普通 Email 重试分支。

## 验证建议

当前 release 测试更多是本地 Job/fixture，而非稳定 api-test harness。改动时应记录使用的具体测试、是否覆盖四类来源和统一回调；未覆盖的来源必须明确列为验证缺口。
