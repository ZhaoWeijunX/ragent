---
title: BILL/BL 接单 API 与协作
module: bill-bl-intake
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# BILL/BL 接单 API 与协作

## 证据合同与实现核验

前端入口：`BLEntrustedInfoController` 的 `/api/v1/bl/info/**`，文件为 `BLEntrustedInfoFileController`，字段来源为 `BLFieldSourceController`/Link，异常为 `BLEntrustedExceptionController`，OpenAPI 为 `BlOpenApiController`，账号映射为 `BLMailAccountMappingController`。Agent/OpenAPI 回调由 `BLCallbackController` 进入 `BillOfLadingCallbackManager`；该 Controller 还按 `dataType=6/7` 分派 VGM/Manifest 回调。

协作边界：BL manager 可通过 `BillClient` 请求 Bill Input 查询/提交/监听，但不能直接改 `biz_bill_record` 或其状态；VGM 联合投影走 `VgmCombinedProjectionManager`。请求中的 cid、来源 BL、`entrustmentIndex` 影响租户和附件隔离，必须在后端校验。代码/文档差异：共享工单入口不代表共享主表。测试为 callback/manager tests；外部 Bill API 鉴权、回调签名、超时和幂等协议当前代码无法确认。源码列表为 controllers/manager/services/client/DTO/entity；最后验证日期 2026-08-26。

`/api/v1/bl/info` 提供 `/page`、`/detail`、`/save`、`/handle`、`/close`、`/repair/blNo`、`/repair/carrier`、`/submitPreview`、`/submitCarrier`、`/forceMonitor` 等接口。`/api/v1/bill/openApi` 提供 SI、付款、公司、城市和配置查询，并有 `/share/*` 版本。

`/api/v1/bl/callback` 接受统一 CallbackDTO；当前代码按 dataType 分派 VGM、Manifest 或 BL。BL 回调还会异步转发 legacy bill-mgmt，转发失败不应被误认为本地 BL 状态已回滚。

涉及字段来源、分享 token、回调或 VGM 联合提交时，应同时检查对应 Manager 与快照日志，而不是只看 Controller。

## 提交与回调协议的实际分工

`BLEntrustedInfoController#submitPreview` 只接受包含 ID 的 DTO 后交给 Manager；`submitCarrier` 传入 `BLInfoSubmitCarrierDTO`，其中截图 key 和可选的 `skipPreviewCompare` 由 Manager 校验和选择性透传。后者只在非空时写入 payload，目的是不改变历史调用未传该字段时的下游默认语义。前端不要据此假设空值与 `false` 等价。

`BLCallbackController` 是回调的路由边界，真正的 BL 状态判定在 `BillOfLadingCallbackManager`。该 Manager 先按 taskNo 找本地 `BLEntrustedInfo`，读取回调的 `billStatus`、`notifyCode`、业务/通知消息，再计算从 beforeStatus 到 afterStatus 的更新。找不到 taskNo 会抛参数异常；草稿超时只发通知、不更新状态；未知映射会记录而不强制改状态。调用方若要重放，必须保留原 taskNo、状态和 payload，而不是构造一条只有“成功/失败”的简化回调。

## 协作约束

测试应至少验证三类合同：前端 DTO 的必填和空值语义；`BillClient` 按 cid + 委托客户命中配置；回调在关闭、迟到、重复或未知状态时不破坏当前态。当前源码不能确认网关是否对回调做签名或来源网络限制，因此该安全控制不能写成已实现事实。
