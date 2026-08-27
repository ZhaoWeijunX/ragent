---
title: 独立 VGM Input API 与协作
module: vgm-input
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 独立 VGM Input API 与协作

Web 接口包括 `/api/v1/command/vgm/createCollect`、`collect`、`queryVgmInfo`、`submit`、`update`、`page`、`detail/{id}` 和容器查询。内部 API 包括 `/openApi/v1/internal/vgm/submit`、`monitor`、`/openApi/v1/vgm/queryVgmInfo` 和 `/openApi/v1/task/vgm/receipt`。

Provider 对提交、监听、回执使用 Redis 锁；回执通过 task 查找 `BizCustomerTask`，再调用 `BizCommandVgmManager.vgmInputReceipt`。接单侧或 BL 可以调用这些接口，但不拥有通道任务状态。

## 契约与调用方责任

Web Controller 从登录上下文取得租户；内部 OpenAPI DTO 必须显式携带 tenant、carrier、account、bookingNo、source 等契约字段。调用方负责自己的业务前置条件和结果投影，VGM Input 负责官网任务、回执和通道当前态。`source` 用于区分调用场景，不改变领域所有权。

`queryVgmInfo` 经 `VgmWebsiteInfoQueryService` 保持统一官网查询；调用方不要直接拼 Cluster 请求。`openVgmMonitor` 在锁内查询/创建监控上下文，返回记录 id 只表示受理，不能当作官网已提交。

## 错误、安全与兼容

接口需区分参数校验、账号/配置不支持、外部 HTTP、外部业务 code 和回执状态冲突。日志保留 taskNo、租户和业务号但脱敏账号凭据。前端重试、Provider 重试和 Job 重扫会产生至少一次语义，后端必须幂等。

## 文档差异与验证

设计 API 是协作基线，当前 `ICommandVgmInputOpenApi`、Provider 和 DTO 决定真实协议。未在契约出现的字段不能按设计示例假定可用。验证包含 Web/内部两类入口、锁竞争、重复 receipt、错误 body 与旧状态。生产网关鉴权、超时和调用版本当前代码无法确认。来源：Controller、OpenAPI、Provider、Manager、BillClient、ClusterOpenApiService 与 DTO tests。
