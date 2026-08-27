---
module: manifest-input
title: Manifest Input 排障与验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-docs-and-code-only
---

# Manifest Input 排障与验证

按 Provider→Processor→Rule→Record/Mongo→CustomerTask→dispatch→Cluster→receipt 顺序留证。先取得 cid、carrier、channel、accountId、businessNo、identity、taskNo；查 `biz_manifest_record` 的 status/error/latest operation/data_id，再查任务和操作历史。提交中不代表官网成功，40/50 需来自回执处理。

重复创建查 identity 唯一索引及逻辑删除；规则失败查 validation context 和船司策略；无任务查 Handler 与 dispatch 筛选；回执丢失查 taskNo、契约路由、receipt transaction 和 notification 日志；状态倒退查旧 task/重提版本。接口静态可用 `rg -n "manifest/submit|manifestInputReceipt|ManifestReceipt"`，SQL 可核对 DDL 索引。

当前仅有源码、模型和 SQL 静态证据，未执行真实 Cluster/API-test 或外部官网调用；不得将设计文档目标当运行结果。未知项：线上队列积压、第三方响应原文、任务重试次数。源清单：Provider、Processor、dispatch、receipt、两张 Manifest SQL。

## 文档与代码差异

设计稿中的全链成功场景不能替代本轮运行证据；当前 checkout 也只发现 COSCO 官网规则策略。若问题涉及其他船司、生产调度或外部官网，应记录为“当前代码/环境无法确认”，不能用设计示例解释成现状。

## 分层排障矩阵

| 现象 | 第一证据 | 误判风险 |
| --- | --- | --- |
| 接口成功但未执行 | customerTask、status=10、dispatch 索引条件 | HTTP 成功不等于官网成功 |
| 同身份重复 | `uk_manifest_identity`、identity_active、并发日志 | 只按 businessNo 去重 |
| 旧回执覆盖新提交 | taskNo、submitProcess、到达时间 | 最后到达不一定最新 |
| 状态已更新但无通知 | receipt 事务提交与 Dispatcher 日志 | 通知异常不代表状态回滚 |
| 操作历史重复 | operationHash 与唯一键 | 只看 latest_operation |
| 规则失败 | Registry 命中策略和校验上下文 | 推断其他船司已实现 |

## 最小证据包与验证边界

每次故障收集 cid、manifestId、identity、carrier/channel、accountId、dataId、customerTaskId/taskNo、submitProcess、status、error/statusMsg 和 operationHash，并交叉核对 MySQL、Mongo、任务和原始回执。缺 taskNo 无法证明回执属于哪一轮，缺 dataId 无法证明发送快照与当前态一致。

现有规则、持久化、回执和调度测试是本地证据，不替代真实 Cluster、API-test 或官网操作。线上队列积压、账号限流、回调签名、Mongo 集合命名及重试次数当前代码无法确认，必须明确标记为运行态待证。
