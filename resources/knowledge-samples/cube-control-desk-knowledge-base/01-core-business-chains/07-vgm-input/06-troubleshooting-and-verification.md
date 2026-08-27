---
title: 独立 VGM Input 排障与验证
module: vgm-input
doc_type: troubleshooting-and-verification
audience: backend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---

# 独立 VGM Input 排障与验证

## 排查时间线

用 tenant + carrier + bookingNo + taskNo 串起 Web/OpenAPI 请求、`biz_vgm_record`、containers、Mongo 快照、customer task、dispatch、receipt 和官网查询。先判断“未受理、未下发、外部失败、回执未收敛、轮询未完成”哪一阶段断开。

## 故障矩阵

| 现象 | 首查 | 关键判断 |
| --- | --- | --- |
| 提交提示不支持 | carrier/channel、RuleTools、config | 当前是否存在对应 WEB 策略 |
| 查询官网失败 | account、bookingNo、Cluster status/body | 配置/账号错误还是外部协议错误 |
| record 有、task 无 | Processor/Handler 事务与异常 | 记录保存后任务创建是否失败 |
| task 成功、record 未变 | taskNo、receipt 路由、旧状态 | 回执未到、重复/晚到被拒绝 |
| 部分容器失败 | container 状态、原始回执 | 主状态聚合是否保留逐箱错误 |
| 长期 SUBMITTING | Poll/Timeout Job、next scan、锁 | 调度未启用、查询失败或锁竞争 |
| 接单侧无结果 | 通道 taskNo、BLCallback/VgmCallback | 通道成功与上游投影是两阶段 |

## 验证层级

Rule/Processor 单测验证 carrier/channel、字段和容器；Handler/Receipt 测试发送重复、旧状态和部分失败；Controller/OpenAPI 测试验证协议与锁；隔离 api-test 检查记录、任务、回执和最终状态。外部不可用时使用 mock 并明确不能证明官网实际行为。

## 风险、差异与未知项

不要直接重发官网任务来修复“本地未更新”，外部可能已经成功；优先重放幂等回执或补偿本地。设计中的船司/状态需以当前策略和 Handler 为准。生产 Job cron、executor、官网 SLA、账号有效性和告警阈值当前代码无法确认。

## 来源与面试

来源：`CommandVgmInputOpenApiProvider`、`BizCommandVgmManager`、`VgmWebsiteInfoQueryService`、Processor/Rule/Handler、Poll/Timeout Job、任务与 VGM 实体/SQL/tests。面试追问：如何处理轮询与回调竞态、如何安全重放、如何证明最终完成而非仅 HTTP 受理。
