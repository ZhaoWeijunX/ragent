---
module: manifest-input
title: Manifest Input API 与协同
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest Input API 与协同

契约 `ICommandManifestInputOpenApi` 提供 `POST /openApi/v1/internal/manifest/submit`，配置 search/searchAll，官网信息、HS Code、客户类型查询，以及 `/openApi/v1/task/manifest/receipt`、`/operation/receipt` 两个回执。Provider 是唯一对内编排入口；API model 的 `ManifestInputOpenApiParam`、`ManifestData`、Receipt DTO 不应直接替代内部 Mongo/实体。

Intake manager 通过 `ManifestSubmissionService` 调用 submit；BL/VGM 等调用方如需复用，应走该契约并明确 source、cid、carrier、channel、account 与 business identity。dispatch 层读取状态为 10 且未删除的记录和账号维度索引，任务执行器回调必须带 taskNo。回执先本地事务，再通知，避免下游通知异常导致状态回滚。

接口必填字段以 DTO 校验注解为准；仓库能确认路径与返回包装，无法确认网关鉴权、超时和生产重试。跨模块改动需联查 API、Provider、Processor、TaskQuery、ReceiptManager 及通知观察者。源清单：契约、Provider、API DTO、ManifestApiTaskDispatchService、ManifestInputTaskQuery、ReceiptManager。

## 文档与代码差异

设计接口说明用于协作，但当前 `ICommandManifestInputOpenApi`、Provider 和 DTO 决定真实可调用字段。设计中未落到 DTO 的参数不能作为已支持协议；代码中已有但设计未同步的字段，应按兼容变更处理而不是让调用方猜测。

## 并发与事务边界

多个调用方可并发提交同一 identity，最终去重依赖数据库唯一键和当前动作替换逻辑；Provider 本身不是分布式锁。回执事务保护本地数据库操作，但 MySQL、Mongo、CustomerTask、通知和外部官网不构成全局事务。调用方重试必须结合任务键与当前态判断，不能假设同步调用 exactly-once。

## 调用方、下游与协议边界

已知上层调用方是 Manifest Intake 的 `ManifestSubmissionService`；它把接单侧身份和标准舱单数据转换为 Input 请求。下游依次为 Processor、RuleStrategy、账号/资源服务、MySQL/Mongo、CustomerTask、dispatch 和 Cluster，任务执行后再通过 task receipt 回到 Provider。调用方不得直接写 `biz_manifest_record`，也不得把 `manifest_entrusted_info` 的来源关系当作通道字段。

`ManifestInputOpenApiParam` 携带真正提交的 `ManifestData`；配置、HS Code、客户类型查询 DTO 只提供辅助选择。接口返回成功仅表示请求被接收，调用方应保存 manifestId、customerTaskId/taskNo 并等待回执或查询当前态。网关鉴权、超时、生产重试和回调签名当前代码无法确认。

## 兼容与验证

新增字段必须同时确认 DTO 校验、转换器、Mongo codec 和船司规则，否则会出现“接口接收但快照丢失”。回执扩展应容忍未知官网原文状态并保持 taskNo 定位。`ManifestDataContractTest`、查询参数契约测试、`ManifestSubmissionServiceTest` 和回执事务测试可证明本地契约；真实 Cluster/API-test 仍需独立证据。
