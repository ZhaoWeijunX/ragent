---
title: Bill Input 提单补料模块概览
module: bill-input
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# Bill Input 提单补料模块概览

Bill Input 是官网提单采集、校验、提交、回执、文件监听、识别与比对基础能力。Web 入口为 `BizCommandBillInputController`，内部契约为 `ICommandBillInputOpenApi`，编排为 `BizCommandBillManager`，提交处理由 `BillInputConfirmProcessor`/`BillInputUpdateProcessor` 完成，状态和文件推进集中在 `BillRecordHandler`。

该模块主数据是 `biz_bill_record`，详情为 Mongo `MongoBizBillRecord`，文件为 `biz_bill_file_record`；任务使用 `TaskCommandEnum.BILL_INPUT`。BL Intake 可以调用它，但两者不是同一主业务域。

## 阅读边界与职责

本文面向接手 Bill Input 的后端、测试和运维人员。范围是提单补料通道侧从采集到官网提交、回执、文件监听和识别；不包含 BL Intake 工单主表、接单状态，也不把 `vgm_info` 或旧租户账号表当作本模块数据。代码事实是：`BizCommandBillInputController` 负责 Web 参数装配，`CommandBillInputOpenApiProvider` 负责内部契约和回调入口，`BizCommandBillManager` 组织任务与业务动作；`AbstractBillInputProcessor` 用模板方法固定准备任务、转换校验、清洗、保存和状态机推进。

## 运行时位置

请求先进入 `biz_task`/`biz_customer_task` 任务壳，再落 `biz_bill_record` 和 Mongo `MongoBizBillRecord`。官网执行由集群 OpenAPI 下发，结果由 `billInputReceipt`、`billFileReceipt`、`billIdentificationReceipt` 等回调推进。`BillRecordHandler` 是状态和文件后处理集中点；改状态必须同时检查任务状态机、记录状态、文件记录和 schedule job。

## 设计理由、限制与证据

模板处理器把通用校验与船司差异规则分层，TEMP 只保存本地记录，避免暂存误触官网。关系库与 Mongo 分离适配当前态查询和表单快照，但当前代码无法确认两库具备跨库原子事务。已核验：`BizCommandBillInputController`、`CommandBillInputOpenApiProvider`、`BizCommandBillManager`、`AbstractBillInputProcessor`、`BillRecordHandler`、`BizBookingCarrierConfigService`、`BizBookingCarrierConfig`、状态枚举及 `CommandBillInputOpenApiProviderCleanDataTest`。外部官网 SLA、回调重试次数和生产告警阈值当前代码无法确认。最后核验 2026-08-26。
