---
title: 独立 VGM Input 开发指南
module: vgm-input
doc_type: development-guide
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 独立 VGM Input 开发指南

## 从真实链路定位

Web 变更查 `BizCommandVgmInputController` → `BizCommandVgmManager`；内部协议查 `ICommandVgmInputOpenApi` → `CommandVgmInputOpenApiProvider`；提交规则查 Processor/`VgmInputRuleTools`/船司策略；状态查 `VgmRecordHandler`；官网查询查 `VgmWebsiteInfoQueryService`/Cluster；超时与最终状态查 Poll/Timeout Job。

## 扩展船司、字段和状态

新增船司官网规则需要注册 `{carrier}_WEB` 策略、确认 channel 映射、账号查询、配置模型、清洗 DTO 和测试；不能因为枚举中有某渠道就宣称支持。新增字段要覆盖 Web/OpenAPI DTO、Processor 转换、Mongo 快照、MySQL/容器（仅在需查询时）、回执和详情回显。新增状态要检查 Handler、轮询、超时、列表和所有 switch。

## 并发与事务门禁

复用 submit/monitor/receipt 现有 lock 语义，并增加历史幂等条件；外部调用不要放进不必要的长数据库事务。主记录、容器、Mongo 和 customer task 的写入顺序要支持重试，任何部分失败保留 task/data id。轮询结果和回调更新使用旧状态守卫，避免终态回退。

## 测试与发布

覆盖参数/账号/配置、策略选择、容器边界、首次/失败重提、重复回执、并发轮询、外部 4xx/5xx/空 body、超时和 Mongo round-trip。SQL 变更给存量数据、唯一索引、回滚与后检；Job 变更评估扫描量和重复副作用。

## 差异、风险和面试

当前代码只直接发现 COSCO 官网策略，设计可扩展性不是已支持船司清单。生产调度、容量和外部 SLA 需环境确认。面试可从策略注册、状态聚合、锁/幂等和跨存储补偿追问。来源为 VGM Input Controller/Provider/Manager/Processor/Rule/Handler/Job/Entity/SQL/tests。
