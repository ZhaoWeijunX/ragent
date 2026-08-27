---
title: Job 重试调度与脚本排障
module: job-retry-scheduling-and-scripting
doc_type: troubleshooting-and-verification
audience: backend-ops-test
last_verified: 2026-08-26
---

# 排障与验证

## 竞态验证

除到期扫描、handler 注册和 attempt 外，应验证外部成功但本地更新前宕机、两个节点并发扫描、成功回执与超时同时到达。Bill 文件链需断言 DRAFT 后只创建一个 COPY，VGM 链需断言 taskNo 终态不被超时覆盖。

XXL-Job 注册、集群抢占、时钟、线程池、告警和脚本权限不由静态源码证明。

先查任务表中的业务 id、scene/type、状态、attempt、nextExecuteTime，再查 Job 是否扫描、handler 是否匹配、外部调用和最终状态。重复执行重点验证幂等；没有执行查租户开关、调度注册和时间窗口；反复失败查异常分类、重试上限和脚本返回。`MAIL_RELEASE_FAILED` 需关联 retry task 与邮件记录。

验证覆盖到期/未到期、并发重复、成功关闭、失败递增、handler 缺失、脚本异常和 Job 超时。现有代码无法确认生产调度器注册、并发上限、告警和脚本权限。证据：`BusinessRetryJob`、`BillFilePullJob`、VGM Jobs、retry handler、SQL；最后核验 2026-08-26。

## 故障矩阵

| 现象 | 代码侧检查 | 数据侧检查 | 不能直接下结论的事项 |
| --- | --- | --- | --- |
| 到期任务未执行 | Job handler 名称、扫描条件、租户开关 | 状态、nextExecuteTime、类型、逻辑删除 | 仅有任务记录不能证明调度中心已注册 |
| 一直被重复扫描 | 成功分支是否关闭任务、异常是否被吞 | attempt、最后错误、完成时间 | 不能把重复执行直接归因于调度器 |
| handler 未匹配 | scene 枚举与 Bean 注册 | scene 原始值 | 数据修正前先确认历史版本兼容性 |
| 外部成功但本地失败 | 回执幂等、事务边界、条件更新 | 业务当前态与任务态 | 外部副作用通常无法随本地事务回滚 |
| 脚本执行异常 | 脚本 key、输入上下文、返回契约 | 配置版本、租户范围 | 当前仓库无法证明生产脚本沙箱权限 |

验证应构造两个并发执行者处理同一任务，确认只有一个业务结果生效；再模拟“外部调用成功、本地提交前异常”，观察重试是否会产生重复副作用。对 Bill 文件监听还要验证 DRAFT 成功后 COPY 任务是否只创建一次，对 VGM 轮询要同时验证超时 Job 与成功回执竞争时的终态。静态源码只能证明扫描与分派逻辑存在，XXL-Job 注册、机器时钟、线程池、告警规则必须用运行态证据补充。
