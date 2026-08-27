---
title: Agent RPA 集群与任务调度集成配置
module: agent-rpa-cluster-task-dispatch
doc_type: integration-and-configuration
last_verified: 2026-08-26
---

# 集成与配置

业务方提供 taskNo、命令和结构化 payload，`AsyncAgentHelper`/`AgentServiceUtil` 组装 `AgentConfigDTO`，`AgentHttpService` 调用 Agent；RPA/IM 回传由 `RpaPullImMessageOpenApiProvider` 接收，再由业务 callback 负责状态推进。公平调度入口由 API/RPA scheduling controller 暴露，实际分配规则以 dispatch service 为准。

配置变更需同步 Agent 地址/超时、集群节点、消息类型、回调解析和失败重试；敏感凭据不可写入日志。不要在 Agent 层硬编码业务状态或以 HTTP 200 代替业务结果判断。

证据合同：调用方为各业务 Job/Task，受方为 Agent HTTP、dispatch service、RPA message provider；测试 `RpaChannelMsgTest`、`EmailAgentTest`、`RpaDispatchServiceAgencyTest`。代码/文档差异：异步响应需要业务回调确认；未知项：认证、节点健康检查、重试退避当前代码无法确认。源码列表为 agent core/biz、dispatch、provider/controller、entity/SQL；最后验证日期 2026-08-26。

## 源码调用细节

`AgentHttpService` 按 env 选择 `agent.base-url.beta/prod`，拼接 `/next-public-api/v1/bot/{botId}/chat/simple`。
请求 header 为 `x-bot-channel-id`、`x-bot-token`，连接超时 10 秒，整体超时 300 秒。
`AgentServiceUtil#sendMessage` 复用 `simpleMessage`，邮件解析等 Agent 业务通过该层发起请求。
`RpaDispatchService#callRpaOpenApi` 按 businessType 和 booking resource clusterId 选节点与机器人。
随后使用 cluster AK/SK、talent account、robotId、paramName 和 payload 调用 RPA OpenAPI。
`RpaPullImMessageOpenApiProvider#pullImMsg` 带事务，并按租户外部消息时间段筛选入站消息。
旧 `TaskDispatchEventListener` 注释称已过时，当前调度在 dispatch Job，不能误作主链。
请求、回调、任务状态是跨系统步骤，认证、退避、健康检查和 outbox 当前代码无法确认。

## 变更影响

修改 endpoint、超时、认证、节点选择或消息 schema 时，要同步 dispatch、Agent HTTP、RPA 入站 provider 和业务 callback；payload schema 由业务方拥有，敏感字段必须脱敏。当前代码无法确认 outbox、退避和健康检查，不能承诺自动切换。面试追问包括 HTTP 200 与业务码、超时重试幂等、回调签名和节点租约。
