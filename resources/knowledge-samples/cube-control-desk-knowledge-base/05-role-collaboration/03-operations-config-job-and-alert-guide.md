# 运维配置、Job 与告警指南

> 最后核验：2026-08-26。本文不包含生产连接信息，也不授权执行脚本或 Job。

## 配置变更

变更必须明确 cid、carrier、channel、配置类型/key、旧值、新值、解析模型、缓存刷新和回滚值。对 `sys_config`、租户配置、`biz_booking_carrier_config`、账号和 Groovy 脚本分别使用其所属管理入口，不能直接复制其他租户配置。

## Job 运维

XXL/Quartz Job 的关键证据包括 handler 名、executor、参数、cron、分片/并发、扫描范围、上次/下次时间和失败重试。手工触发前先用只读查询估算命中数据，确认 Job 是否有外部发送、状态推进或文件操作副作用。

```mermaid
flowchart LR
    Config[配置/账号/脚本] --> Job[Job 扫描]
    Job --> Task[任务/外部调用]
    Task --> Callback[回执]
    Callback --> Metric[成功/失败/积压/延迟]
    Metric --> Alert[告警与处置]
```

## 建议观测项

任务积压、最老等待时间、回调延迟、失败率、重试耗尽、状态漂移、邮件游标停滞、文件监听超时、通知失败和外部客户端错误分类。告警应关联业务 id/taskNo，避免只能看到异常总数。

## 故障处置边界

优先暂停扩大影响的调度/配置，再保存证据；重放前确认幂等和当前状态；SQL/Groovy 修复要有备份、命中行数、事务/批次、回滚与后检。不能把删除任务作为修复业务当前态。

## 差异与未知项

仓库能证明 Job handler、配置读取和日志点，无法确认生产 cron、告警平台、阈值和应急审批。来源：scheduler 配置、`@XxlJob`、BusinessRetry、SysConfig/Tenant/CarrierConfig、scripts/sql、日志与状态实体。面试追问：安全重放、配置灰度、积压告警和补偿审计。

