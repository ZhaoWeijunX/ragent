---
title: 接单侧 VGM Intake API 与协作
module: vgm-intake
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 接单侧 VGM Intake API 与协作

`VgmInfoController` 提供 `/page`、`/statusCount`、`/detail`、`/precheck`、`/activeCheck`、`/createFromBill`、`/save`、`/handle`、`/submit`、`/close`、`/remark` 和 `/operationHistory`。

联合提交由 BL `submitCarrier` 触发，BL 回调成功后由 `BillOfLadingCallbackManager` 检查最新提交快照和 VGM 模式，再调用 `VgmCombinedProjectionManager`。独立提交通过 `ICommandVgmInputOpenApi` 的 `/openApi/v1/internal/vgm/submit` 与回调协作。

能力解析由 `VgmConfigResolver#resolve(cid, customer, carrier)` 返回 combined/standalone 能力；配置来源分别是 Bill Input fieldConfig.vgmInput 与 `VGM_SUBMISSION:{tenantId}`。

## 前后端契约

独立保存/接单/关闭/备注/提交不要求前端传 cid，Manager 从登录上下文推导租户。提交请求与 BL 提交对齐，只需 VGM id 和 `screenshotFileKey`；页面不应自行拼接通道 DTO。列表和详情的 `relatedBlWorkOrders` 承载来源 BL 及打开异常，VGM 顶层不重复制造 BL 异常字段。

前端展示 `combinedEnabled`/`standaloneEnabled` 只是能力提示，后端在 create/submit 时仍校验配置、来源、状态、账号和官网 precheck。重复点击由前端禁用按钮改善体验，但真正幂等依赖后端锁、状态和 taskNo。

## 与 Input/回调协作

接单侧通过 `BillClient`/`ICommandVgmInputOpenApi` 消费 VGM Input，不能直接写 `biz_vgm_record`。回调统一从 `BLCallbackController` 按 dataType 分派到 `VgmCallbackManager`；HTTP 成功不等于接单侧已更新，要检查 taskNo 能否定位和旧状态是否允许迁移。

## 差异、验证与来源

`doc/design/vgm/vgm_import_frontend_api.md` 与 BL/VGM 前端设计是协作基线，当前 Controller/DTO 优先。仓库能静态确认路由和字段，无法确认前端当前分支、网关鉴权与生产错误码映射。验证覆盖无 cid 用户接口、越权来源 id、重复提交、联合记录禁止人工操作及回调错误。来源：Controller、DTO、Manager、ConfigResolver、BillClient、OpenAPI 契约与设计文档。
