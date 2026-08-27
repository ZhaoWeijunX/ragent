---
title: Release 开发定位指南
module: release
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Release 开发定位指南

## 证据合同与实现核验

改动前按 `CommandBookingOpenApiProvider → task → 对应 Job → strategy → releaseSpaceCallback → BizAdvanceBooking/Ext` 定位，并同时读监听表、配置和 retry handler。新增监听来源必须实现统一结果映射；修改当前态时不能只改历史记录。需覆盖 pending/confirm/update/cancel、重复回调、游标推进、非 ASTA 可重试异常、配置关闭和多租户隔离。

代码证据为四类 Job/strategy、Provider、callback、release service/entity/SQL；测试为四类 Job 单测。代码/文档差异：重试模块不替代旧 Push Retry；外部协议、锁和线上 SLA 当前代码无法确认。源码列表与最后验证日期：2026-08-26。

## 推荐顺序

先确认问题属于任务创建、来源监听、策略解析、统一回调还是回填脚本；随后依次查 Provider、对应 Job、Strategy、Release 状态机、Service/Mapper 和模型。

## 修改检查

- 监听来源是否只修改了自己的表和策略。
- 结果是否最终进入 `releaseSpaceCallback`。
- 当前态与历史快照是否分别更新。
- `pending` 是否被错误当成失败。
- Email 可重试异常是否提交了 `MAIL_RELEASE_FAILED`，重试 handler 是否幂等。
- 修改放舱监听、策略或回调时，需补真正覆盖主链的 api-test；当前 checkout 的邻接测试不足以证明主链。

`doc/onboarding/booking-release-quick-start.md` 是现有快速入门；`doc/design/release/` 下设计文档可能记录演进或待修复问题，必须与代码交叉核验。
