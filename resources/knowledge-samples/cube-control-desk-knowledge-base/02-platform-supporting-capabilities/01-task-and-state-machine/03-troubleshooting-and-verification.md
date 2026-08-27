---
title: 任务与状态机排障验证
module: task-and-state-machine
doc_type: troubleshooting-and-verification
last_verified: 2026-08-26
---

# 排障与验证

任务不执行：先查 `biz_task`、`biz_customer_task` 是否创建，再查命令/业务类型、状态、到期时间和 Job 扫描条件。回调找不到任务：核对 taskNo、业务 ID、租户和 callback 入参。任务已完成但页面异常：回到对应业务当前态表，不要只看 task 日志。重复回调：检查状态事件、日志和业务幂等条件。

验证顺序为 Controller/OpenAPI → task service → mapper/SQL → Job → callback → 业务当前态；单测优先 `TaskJobTest`、`CustomerTaskExtServiceTest`，必要时运行相关业务测试。Maven 依赖下载失败不证明代码失败。

现场证据应包含租户、taskNo、command、业务 ID、状态迁移前后值和对应日志时间；若涉及重试，还要记录同一业务是否生成多个任务。跨表查询要分别核对任务壳、扩展 JSON 和业务表，避免把“任务存在”误报成“业务完成”。

证据合同：真实代码为 `TaskController`、`TaskOpenApiProvider`、`TaskManager`、`BizTaskServiceImpl`、`BizCustomerTaskServiceImpl`、enums/entity/SQL。代码/文档差异：不能把通用任务状态解释为业务状态。未知项：生产 trace、告警阈值、锁和补偿任务当前代码无法确认。最后验证日期 2026-08-26。

## 故障矩阵与验证边界

|现象|首查|边界|
|---|---|---|
|无任务|创建日志、task/业务键、事务结果|不能断定业务未触发|
|卡住|状态、到期时间、Job 条件、领取日志|区分未领取与已外调|
|回调丢失|taskNo、provider 入站、业务 callback|access log 不足|
|页面异常|业务当前态、任务日志、状态事件|任务完成不等于业务成功|

排查顺序是 Controller/OpenAPI→task service→Mapper/SQL→Job→callback→业务表；现场记录 cid、taskNo、command、业务 ID、状态前后值、日志时间和重试次数。单测只证明局部分支，集成测试证明序列化/数据库，运行态才证明调度和外部回调。并发领取、乱序消息、补偿窗口当前代码无法确认。
