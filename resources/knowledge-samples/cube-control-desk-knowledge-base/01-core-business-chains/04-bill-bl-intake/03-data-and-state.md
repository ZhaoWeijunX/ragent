---
title: BILL/BL 接单数据与状态边界
module: bill-bl-intake
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# BILL/BL 接单数据与状态边界

## 证据合同与实现核验

BL 当前态是 `bl_entrusted_info`（实体 `BLEntrustedInfo`），工单是 `bl_work_order`，详情快照为 Mongo `BLEntrustedInfoDetailDocument`，文件为 `bl_entrusted_info_file`，异常为 `bl_entrusted_exception`。实体字段包含 `bizStatus/bizSubStatus`、`bookingNo`、`blNo`、拆合单字段、`openExceptionCount`、`taskNo`、`vgmSubmitMode`。历史/操作证据由 `sys_operation_log` 与 snapshot 记录。

`BLEntrustedInfoManagerImpl#close/handle` 先 `getAndCheck`，按枚举允许列表校验，再更新状态；关闭还解决异常、投影异常、同步重复/拆单组并记录操作日志。拆单按组处理，避免成员状态半组。状态真源是 BL 当前表，不是 Bill Input 的 `biz_bill_record`。代码/文档差异：VGM 投影和通道侧记录要区分。测试为 close/forceMonitor 与 callback tests；跨 MySQL/Mongo 一致性、回调幂等和外部事务当前代码无法确认。源码列表为 entity/enum/manager/service/Mongo/SQL；最后验证日期 2026-08-26。

## 核心数据

`bl_entrusted_info` 保存提单接单主数据，`bl_work_order` 保存 BL 工单，`bl_entrusted_info_file` 保存文件，`bl_tracking_event` 保存 tracking 事件，`bl_entrusted_exception` 保存异常，`bl_vc_mail_send_log` 保存 VC 邮件发送记录，`bl_mail_account_mapping` 保存邮箱映射。详情、字段来源与来源链接分别由 `BLEntrustedInfoDetailDocument`、`BLFieldSourceDocument`、`BLFieldSourceLinkDocument` 承载。

`BLEntrustedInfoStatusEnum` 是接单侧状态来源；回调映射由 `BillOfLadingCallbackManager.mapAfterStatus` 实现，覆盖提交成功、提交失败、预览异常、草稿件、Copy 件等分支。具体状态值必须以当前枚举和映射代码为准。

BL 与 `entrusted_work_order` 不共用主表；BL 与 `biz_bill_record` 也不是同一业务记录。

## 状态与快照如何共同工作

接单侧的可查询当前态以 `bl_entrusted_info.biz_status` / `biz_sub_status` 为准；Mongo detail 是可编辑表单和版本快照，不应反向推断状态。提交预览和提交船公司时，Manager 复制当前表单、补充拆单和 VGM 模式后写入操作 history：`submissionPayload` 是可复盘的业务发送内容，截图单独进入 extra data，避免一次性凭证污染可编辑表单。

`taskNo` 是本地单据与通道回执的关键关联字段。`submitPreview` 和 `submitCarrier` 都只在 client 返回 data 时回写 taskNo；`forceMonitor` 则要求返回任务号。因而“当前没有 taskNo”既可能是远端未返回，也可能是尚未进入需要通道异步回执的步骤，不能只按空值判定失败。

回调状态映射还会受进入回调前的状态影响。例如 `SAVE_DRAFT_SUCCESS` 映射到 `WAIT_SUBMIT_CARRIER`；`SUBMIT_SUCCESS` 只有带成功通知码时才落到 `SUBMITTED_CARRIER`；审核异常、预览比对差异和提交失败会落入不同异常状态。已关闭单据在 `mapAfterStatus` 中不再流转，这防止迟到回调复活业务单，但也意味着关闭后的远端结果需单独审计。

## 一致性限制

MySQL 主表、Mongo detail、文件表和操作日志并非一个数据库事务。代码能保证 Manager 事务内的关系型更新回滚，却无法从源码证明 Mongo 写入、文件落库、远端调用与它们的原子性。修改数据模型时，应把“当前态、可编辑快照、不可变操作证据”分别建模，并补覆盖失败后重试与迟到回调的测试。
