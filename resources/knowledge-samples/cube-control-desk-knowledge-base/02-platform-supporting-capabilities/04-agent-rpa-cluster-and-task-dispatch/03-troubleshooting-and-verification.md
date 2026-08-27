---
title: Agent RPA 集群与任务调度排障验证
module: agent-rpa-cluster-task-dispatch
doc_type: troubleshooting-and-verification
last_verified: 2026-08-26
---

# 排障与验证

派发未执行：查 taskNo/命令/payload、调度入口和节点选择；HTTP 失败查 Agent 地址、超时和认证；Agent 已执行但业务未收口，沿 IM/RPA 回传 provider → taskNo 定位 → 业务 callback。重复消息需核对消息 ID、业务幂等和任务日志。

验证优先 `RpaChannelMsgTest`、`EmailAgentTest`、`RpaDispatchServiceAgencyTest`，再做真实 Agent/RPA 场景并记录请求、响应、消息 ID、taskNo 和业务表前后状态。证据合同：源码为 Agent core、dispatch、provider/controller、RPA entity、tests。代码/文档差异：传输层成功不等于业务成功；未知项：生产节点、告警和重放窗口当前代码无法确认。最后验证日期 2026-08-26。

排查时必须脱敏 endpoint 凭据和业务 payload；同一消息要用 message ID、taskNo 和业务主键交叉定位。若 Agent 已执行而回调缺失，分别验证 Agent 结果、RPA 入站消息、provider 解析和业务 callback，不能仅凭 HTTP access log 下结论。

## 源码对照与重试边界

先核对 `AgentHttpService#getBaseUrl` 的环境、botId、header 和固定 API path。
RPA 侧核对 `dispatchNode`、`selectRobot`、clusterId、businessType 与 talent account。
IM 回传核对 `RpaPullImMessageOpenApiProvider#pullImMsg` 的 tenant、时间段和消息更新结果。
HTTP 超时先查对端执行记录，不能直接判定未执行或立即重放。
多个 Job 是否有 claim 锁、租约和统一幂等，当前源码无法确认。
回调重复按 messageId/taskNo/业务主键对账；非幂等业务不得直接重试。
请求日志包含 requestBody，必须验证生产脱敏和访问控制。
测试只能覆盖分支，不能替代真实网络、节点容量和异步时序验证。

## 故障矩阵与验证边界

未派发查 task→command→dispatch claim→节点；HTTP 超时查 endpoint/认证/Agent 结果，超时不等于未执行；已执行未收口查 RPA 入站→provider→callback；重复执行查 messageId/taskNo/业务幂等。验证需记录节点、响应业务码、消息 ID、taskNo、状态前后值和重试次数；单测不覆盖生产网络、节点和回调时序，均为当前代码未知项。
