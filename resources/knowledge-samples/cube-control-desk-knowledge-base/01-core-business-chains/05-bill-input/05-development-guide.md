---
title: Bill Input 开发指南
module: bill-input
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# Bill Input 开发指南

## 变更检查清单

沿 Controller/OpenAPI→Manager→Processor→Rule→Handler→Job 检索真实调用方和被调用方。字段变更同时检查 API/Mongo DTO、Mapper/SQL、清洗 payload、回执和前端 VO。船司差异放 `*_WEB_BillInputRuleStrategy`，通用规则放 `BillInputRuleStrategy`/Processor；TEMP 不执行正式清洗、官网提交和文件监听。

新增状态同步枚举、Handler、状态机、统计查询、测试和文档；新增文件类型同步 submitAction、schedule、PullJob 集合、监控策略、receipt 与识别 Job。账号只用 `BizBookingAccount`，敏感信息不得进日志；回调不可用外部业务号替代 taskNo。锁不替代数据库幂等。

最小验证：策略/清洗异常单测、TEMP、重复 receipt、DRAFT→COPY、不可下载文件；集成 submit→receipt→file receipt。依赖或集群不可用只能报告验证缺口。证据：`CommandBillInputOpenApiProviderCleanDataTest`、`BillRecordHandlerPullFileNotDownloadableTest`；最后核验 2026-08-26。

定位顺序：Controller/OpenAPI -> `BizCommandBillManager` -> Processor -> RuleStrategy/Tools -> `BillRecordHandler` -> Job/Monitor Strategy -> Service/Model。

船司差异规则放在 `*_WEB_BillInputRuleStrategy`；通用上下文规则放在 `BillInputRuleStrategy` 或 Processor。TEMP 不应执行只有正式提交才需要的官网流程。修改文件监听、提交检查或识别时，必须同时看对应 Job、回执和 `pullFileSuccess`，不能只改 `billInputReceipt`。

账号来源是 `BizBookingAccount`；配置类型为 `CarrierConfigTypeEnum.BILL_INPUT`。代码和已有 Bill Input 测试优先于设计/task-pack。
