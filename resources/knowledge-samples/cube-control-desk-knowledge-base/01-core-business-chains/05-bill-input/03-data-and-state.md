---
title: Bill Input 数据与状态
module: bill-input
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# Bill Input 数据与状态

## 数据职责和状态语义

`biz_task`/`biz_customer_task` 是异步任务壳和 taskNo 来源；`biz_bill_record` 保存当前业务状态、账号和错误；Mongo `MongoBizBillRecord` 保存表单快照；`biz_bill_file_record`、`biz_customer_schedule_job` 负责文件和下次执行；`biz_bill_submit_check_record` 负责提交检查，邮件模式还依赖 mail record。`BillSubmitProcessEnum` 的 TEMP/DRAFT/SUBMIT 是请求意图，不能当最终状态。

`TEMPORARY_SAVED(5)` 只代表本地暂存；`SUBMITTING(10)` 已进入官网执行；`SAVED_TO_WEBSITE_DRAFT(20)`、`PREVIEW_FILE_GENERATED(30)`、`SUBMITTED_TO_WEBSITE(40)`、`DRAFT_FILE_GENERATED(50)`、`COPY_FILE_GENERATED(60)` 是阶段事实；`INFO_EXCEPTION(45)` 是审核/信息异常；`SUBMISSION_FAILED(90)` 可修改重提。状态由 Handler 和任务状态机共同推进。

官网提交成功仍可能等待预览、检查或 COPY，不能只看 record status。重复回调、下载失败和识别失败需按 taskNo、文件类型、当前状态做幂等判断。代码无法确认 MySQL/Mongo 跨库事务、保留期限和清理策略。证据：实体/Mapper、状态枚举、Handler、`sql/bill/`；最后核验 2026-08-26。

主记录为 `biz_bill_record`，详情为 Mongo `MongoBizBillRecord`；文件包括 `biz_bill_file_record`、`biz_bill_file_mail_record`；任务为 `biz_task`、`biz_customer_task`，文件调度为 `biz_customer_schedule_job`，提交检查为 `biz_bill_submit_check_record`。

`BillInputStatusEnum` 当前包含 WAITING_SUBMIT、TEMPORARY_SAVED(5)、SUBMITTING(10)、SAVED_TO_WEBSITE_DRAFT(20)、PREVIEW_FILE_GENERATED(30)、SUBMITTED_TO_WEBSITE(40)、INFO_EXCEPTION(45)、DRAFT_FILE_GENERATED(50)、COPY_FILE_GENERATED(60)、SUBMISSION_FAILED(90) 等值；`BillSubmitProcessEnum` 区分 TEMP、DRAFT、SUBMIT。文件类型来自 `BillInputFileTypeEnum`，至少包含 PREVIEW、DRAFT、COPY、AUDIT_FAIL。

状态推进主要集中在 `BillRecordHandler`，不要只看单个回调方法。
