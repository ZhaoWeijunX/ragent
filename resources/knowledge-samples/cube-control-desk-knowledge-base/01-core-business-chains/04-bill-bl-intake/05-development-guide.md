---
title: BILL/BL 接单开发指南
module: bill-bl-intake
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# BILL/BL 接单开发指南

## 证据合同与实现核验

改动前按 `BL controller → BLEntrustedInfoManagerImpl → BL service/exception engine → MySQL + Mongo → callback/client` 追踪，并同时看 `AbstractBillOrderCreate`、字段来源、文件和 VGM 投影调用。新增状态必须同步 `BLEntrustedInfoStatusEnum`、允许操作列表、状态更新 service、列表/详情 VO、操作日志和测试。异常修复要保持打开异常投影与重复/拆单组同步。

不要把 BL 与 Bill Input 主表、状态、文件监听混为一谈；不要为编译绕回旧租户表。测试优先 `BLEntrustedInfoManagerImplCloseTest`、`...ForceMonitorTest`、`BillOfLadingCallbackManagerOriginalIssuedTest`、`...CompareReasonTest`、服务附件/拆单 tests。代码/文档差异及未知项按模块主链；源码列表为 controllers/manager/service/entity/Mongo/SQL；最后验证日期 2026-08-26。

先按 Controller 定位功能，再追 `BLEntrustedInfoManagerImpl`、`BLEntrustedInfoServiceImpl`、异常 Engine/Repair Service 和相关 Mongo Service。提交问题需继续追 `BillClient`、Bill Input 回执和 `BillOfLadingCallbackManager`。

保存提单时要检查异常检测、拆单组同步、VGM 模式和版本号；提交船公司时要检查截图、快照和 `vgmSubmitMode`。不要把文件监听逻辑写到 BL 附件处理里，也不要为了编译复用旧 bill-mgmt 租户表。

现有 BL 设计与 api-test 可辅助理解，但实现行为以当前 Manager、Service、枚举和测试为准。

## 按改动类型定位

新增人工操作时，先在 `BLEntrustedInfoController` 增加最小入口，再在 `BLEntrustedInfoManager`/Impl 定义状态前置条件和操作快照；不要把规则塞回 Controller。若操作会改变异常展示，还要检查 `BLExceptionEngine#projectToInfo` 以及重复单、拆单组同步，否则列表的 `openExceptionCount` 与详情可能分叉。

新增提交通道字段时，从 `submitPreview` 和 `submitCarrier` 的 `formData -> submissionPayload` 路径验证字段是否真的会发送。`submitCarrier` 有截图必填、主拆单就绪、联合 VGM 校验及 `skipPreviewCompare` 的显式透传约束；字段若只写 Mongo detail 而未进入 payload，就不会影响远端提交。反过来，截图属于 extra data，不应回填为表单字段。

新增回调状态时，应同时改 `BillOfLadingCallbackManager#mapAfterStatus`、`BLEntrustedInfoStatusEnum`、列表/详情映射、异常消息长度处理和回调测试。要明确它是状态迁移、仅通知，还是“保留原状态”；已关闭单和草稿超时都是现有的非普通迁移分支。

## 最小验证集

本地单测应覆盖 allowed-status 拒绝、远端成功后 taskNo 回写、远端响应异常、回调前后状态映射和关闭后的迟到回调。端到端场景再使用 bill-desk api-test 验证 HTTP 契约；当前仓库中 api-test 是否能稳定覆盖真实远端 Bill Input，必须以本次运行结果记录，不可预先声称已覆盖。
