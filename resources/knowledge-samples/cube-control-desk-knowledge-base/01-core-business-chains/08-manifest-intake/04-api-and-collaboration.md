---
module: manifest-intake
title: Manifest 接单 API 与协同
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest 接单 API 与协同

## 页面 API

Controller 基路径 `/api/v1/manifest`，实现 `page`、`statusCount`、`detail`、`createFromBill`、`save`、`submit`、`close`、`remark`、`operationHistory`，以及按 `{carrier}/{channel}` 查询枚举、HS Code、官网信息和客户代码类型。页面 DTO（`ManifestEntrustedSaveDTO` 等）与通道 DTO（`ManifestInputOpenApiParam` 等）分层，转换由 manager/codec 完成。

## 通道契约

`ICommandManifestInputOpenApi` 暴露：`POST /openApi/v1/internal/manifest/submit`、配置 search/searchAll、官网信息查询、HS Code、客户类型，以及两个回执 `/openApi/v1/task/manifest/receipt` 与 `/operation/receipt`。`CommandManifestInputOpenApiProvider` 是实现；接单侧不应直接依赖通道数据库或内部实体。

## 协作时序与权限

Controller 标记 `@ApiPermission(false)`，但 manager 仍从 `LoginContext`/`UserContext` 推导租户并执行 owned-record 检查；不要把“免注解鉴权”解释成跨租户访问。`createFromBill` 依赖 BL 服务读取来源单和详情，展示层还会调用 BillClient/客户服务补充信息。异步回执按 taskNo、当前记录和提交记录定位，落库后才发通知。

## 对接契约注意

提交需保留 `source` header、carrier/channel、账号、业务身份和完整 `ManifestData`；回执需要 taskNo、结果对象和操作历史原文。具体必填项以 API DTO 的校验注解为准，不能从页面样例猜测。跨模块变更必须同时检查 `ManifestInputReceiptDTO`、`ManifestOperationReceiptDTO` 与接单 VO。

## 未知项

仓库无法确认外部调用的认证网关、网络超时默认值和生产消息重试次数；这些属于部署配置/运行态证据。

## 源清单

`ManifestEntrustedInfoController`、`ICommandManifestInputOpenApi`、Provider、Manifest API model、`ManifestEntrustedCallbackManagerImpl`、BL 服务接口。

## 文档与代码差异

`doc/design/manifest/manifest_entrusted_frontend_api.md` 是前端协作基线，但 Controller/DTO 是当前协议事实源。设计字段、按钮或状态若未在当前 DTO/Manager 中消费，前端不能仅按设计稿提交；反之，代码新增字段也应回补设计文档并登记兼容性。
