---
title: Release API 与跨模块协作
module: release
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# Release API 与跨模块协作

## 证据合同与实现核验

Release 没有单一前端入口：创建由 `CommandBookingOpenApiProvider` 在订舱回调后按租户/船司能力触发，统一结果回调为 `releaseSpaceCallback`；页面查询由 `CabinManageController` 的 `/release/list`、`/release/detail`。监听协作方是 API、Website、Email、ASTA 四类 Job/strategy，均必须产出 `ReleaseResultDto`。

配置读取依赖 `BizBookingCarrierConfigService`、租户 `releaseConfig` 与脚本/监控配置；邮件失败补偿依赖 `BusinessRetryService`。调用方不应直接改 `BizAdvanceBooking` 或历史表。代码/文档差异：页面历史与当前态需区分；未知项：外部状态码映射和鉴权协议当前代码无法确认。测试为 `ApiReleaseJobTest`、`WebsiteReleaseJobTest`、`EmailReleaseJobTest`、`AstaEmailReleaseJobTest`；源码列表为 controller/provider/jobs/strategies/services/entities；最后验证日期 2026-08-26。

## 统一接口

`ICommandBookingOpenApi` 定义了 `/openApi/v1/command/releaseSpace/{taskCommand}` 创建任务、`/openApi/v1/command/booking/callback/releaseSpace/{taskCommand}` 统一回调，以及 `releaseSpaceFilled` 回调。Provider 实现位于 `CommandBookingOpenApiProvider`。

## 监听来源协作

- API：`ApiReleaseJob` -> `ApiReleaseStrategyImpl`。
- Website：`WebsiteReleaseJob` / `WebsiteReleaseResultObtainJob` -> `WebsiteReleaseStrategyImpl`。
- Email：`EmailReleaseJob` -> `EmailReleaseStrategyImpl`。
- ASTA：`AstaEmailReleaseJob` -> `AstaEmailReleaseStrategyImpl`，复用邮件监控链。

所有来源最终应归一为 `ReleaseResultDto`，再调用正式回调。运营页面 `/release/list`、`/release/detail` 位于 `CabinManageController`，展示层不能替代回调写库逻辑。
