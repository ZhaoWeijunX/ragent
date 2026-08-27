---
title: Bill Input 排障与验证
module: bill-input
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# Bill Input 排障与验证

## 统一时间线

用 taskNo/业务号串起 `biz_customer_task`、`biz_bill_record`、Mongo、file record、schedule、submit-check，按请求→清洗→下发→回执→文件→识别排序。HTTP 200 不代表业务成功，必须读取回执业务状态和外部错误字段。

规则错配查 carrier/channel/策略类名；账号错查 cid、companyCode、过期状态但不打印密码；清洗错查 `secondCleanErrorThrow` 与 formatData；回执丢失查 taskNo、`BILL_INPUT` 命令和 Provider 查找条件；文件不动查 fileType、monitorMode、nextExecuteTime；识别不回填查 file receipt 与 identification Job。

已有证据为 `CommandBillInputOpenApiProviderCleanDataTest`、`BillRecordHandlerPullFileNotDownloadableTest` 和 `../api-test/scenarios/bill-desk/`，不能证明覆盖所有生产配置。外部集群重试次数、告警阈值和数据保留期限当前代码无法确认。最后核验 2026-08-26。

## 常见问题

- 校验不符合预期：查 carrier/channel 规则选择、TEMP/正式分支和规则组。
- 官网提交失败：查 Processor 清洗、账号配置、任务回执与 `BillRecordHandler`。
- 提交成功但无文件：查 `submitAction`、`fileMonitorConfig`、schedule job 和 `BillFilePullJob`。
- 文件有但未回填：查文件 receipt、`pullFileSuccess`、识别 Job 和 identification receipt。
- 提交检查卡住：查 `biz_bill_submit_check_record`、SendJob、TimeoutJob 及回执状态。

验证优先使用 `../api-test/scenarios/bill-desk/` 的 Bill OpenAPI、回调、文件/异常相关场景，并明确哪些只验证局部组件。
