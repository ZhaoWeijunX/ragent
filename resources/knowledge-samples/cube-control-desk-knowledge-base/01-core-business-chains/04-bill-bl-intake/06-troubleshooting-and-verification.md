---
title: BILL/BL 接单排障与验证
module: bill-bl-intake
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# BILL/BL 接单排障与验证

## 证据合同与实现核验

排查顺序：确认来源 record 与 BILL 分派 → 查 `bl_work_order`/`bl_entrusted_info` → 查 Mongo detail/file → 追 manager 操作 → 查 `BillClient` 外调和 callback → 对照异常表、操作日志及 VGM 投影。关闭失败看允许状态/打开异常；页面附件错位看来源 BL 的 `entrustmentIndex` 与 `idpRecordId/recordId` 过滤；回调未收口看 taskNo、状态映射和重复回调。

验证证据：`BLEntrustedInfoManagerImplCloseTest`、`BLEntrustedInfoManagerImplForceMonitorTest`、`BLWorkOrderDetailServiceImplTest`、`BLEntrustedInfoServiceImplAttachmentTest`、`BillOfLadingCallbackManagerOriginalIssuedTest`、`...CompareReasonTest`，以及 `../api-test/scenarios/bill-desk`。风险是 MySQL/Mongo 非原子、外部回调重复、附件跨 BL 泄露、拆单半组和 BL/Bill Input ownership 混淆。代码/文档差异、未知项按上述主链；源码列表为 controller/manager/service/client/callback/entity/SQL；最后验证日期 2026-08-26。

- 无工单：查建单 Job/策略、`bl_work_order`、`bl_entrusted_info` 和异常初始化。
- 无法保存：查版本号、开放异常的 detail/action blocking、异常 Engine 与拆单组校验。
- 提交无回调：查 `BillClient` 请求、任务号、Bill Input 回调和 `BLCallbackController` dataType 路由。
- 状态不符：以 `BillOfLadingCallbackManager.mapAfterStatus` 和 `BLEntrustedInfoStatusEnum` 为准，同时检查 `notifyCode`/`billStatus`。
- 联合 VGM 异常：检查最新 `SUBMIT_CARRIER` 快照、`CONTAINER_AND_VGM` 模式和 `VgmCombinedProjectionManager`。

验证入口包括 `../api-test/scenarios/bill-desk/` 下 BL 工单、回调、异常修复、OpenAPI 分享等场景；场景未覆盖的分支必须单独标注。

## 按状态恢复排障

提交后长期停在 `SUBMITTING_PREVIEW` 或 `SUBMITTING_CARRIER` 时，先查本地操作日志中的 submissionPayload、`taskNo` 和 client 响应，再查该 taskNo 对应的回调是否进入 `BLCallbackController`。不能先人工改状态：`mapAfterStatus` 需要同时看 `billStatus`、`notifyCode` 和回调前状态，错误补写会影响后续预览/草稿/Copy 文件分支。

关闭后仍收到回调不是自动数据损坏。代码对 `CLOSED` 的 beforeStatus 返回空映射，避免迟到回调重新打开单据；应记录原始回调和通知是否已发送，再由业务确认是否需要人工补文件或沟通，而不是重放为普通状态更新。

强制监听或提交船公司失败时，按顺序核对：当前状态是否允许、`screenshotFileKey` 是否存在（仅提交船公司）、合单/拆单主单是否完整、联合 VGM 模式是否可校验、cid + 委托客户是否能解析到 `BillClient` 配置、最后再看远端响应。这样可以将本地前置校验失败与远端受理/回调失败分开。

## 验证结论边界

上述单测与 YAML 场景是代码/合同证据；本次未启动真实外部 Bill 服务，因此没有对网络超时、签名校验、远端幂等或消息投递做运行时断言。涉及这些边界的变更需要在可控联调环境补充带 taskNo 的请求、回调和数据库快照证据。
