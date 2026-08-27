---
title: Job 重试调度与脚本配置
module: job-retry-scheduling-and-scripting
doc_type: integration-and-configuration
audience: backend-ops
last_verified: 2026-08-26
---

# 调度、重试和脚本接入

## 入库到执行

业务方写入业务 id、scene/type、attempt 和 nextExecuteTime，Job 扫描到期记录后路由 handler/strategy，成功关闭或创建后续任务，失败递增 attempt。DRAFT 成功后可能创建 COPY，不能只看原任务关闭。

配置变更作用于存量还是新任务当前代码无法确认，应结合任务表和运行日志验证。

`BusinessRetryJob` 扫描到期 retry task，按业务场景找到 handler；`BillFilePullJob` 扫描 `biz_customer_schedule_job` 并按 `monitorMode` 分派 Website/Mail；VGM `VgmSubmitStatusPollJob` 与 `VgmSubmittingTimeoutMonitorJob` 读取配置决定轮询和超时。脚本 key（如 `RELEASE_SPACE_FILLED_{cid}`）由业务策略解析，脚本结果再回到状态推进。

```mermaid
flowchart LR
 A[业务失败/提交]-->B[(retry or schedule table)]
 B-->C[定时 Job]
 C-->D[handler/strategy/script]
 D-->E{成功?}
 E-->|是|F[关闭/推进状态]
 E-->|否|B
```

配置项包括 nextExecuteTime、重试次数/场景、监听类型和租户脚本；代码无法确认 XXL 调度集群并发、时钟漂移、脚本沙箱和生产参数。来源：Job、handler、schedule/retry entity、SQL 和相关配置枚举；最后核验 2026-08-26。
