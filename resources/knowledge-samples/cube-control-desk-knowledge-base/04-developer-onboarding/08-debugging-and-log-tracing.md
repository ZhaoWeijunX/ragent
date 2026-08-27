# 调试与日志追踪

> 最后核验：2026-08-26。

## 最小追踪键

优先收集 tenant/cid、业务主键/业务号、taskNo、第三方业务号、booking callback no、fileKey、Job 名和时间窗口。单独依赖错误文案很难跨异步阶段定位。

```mermaid
flowchart LR
    W[WebLog/Trace] --> I[入口参数]
    I --> B[业务主记录]
    B --> T[Task/Job]
    T --> X[外部请求]
    X --> C[Callback]
    C --> S[状态/历史]
    S --> N[通知/补偿]
```

`WebLogAop` 提供 HTTP 日志，`TraceXxlJobAop` 关联 Job，业务模块另有 BusinessLog、SysOperationLog、发送日志和回调日志。排障要以数据记录验证日志叙述：日志“发送成功”可能只到网关，HTTP 200 也可能包含业务失败码。

## 方法

先确定当前态真源，再反查最后一次有效操作；比较任务、业务记录、历史和外部回执时间；检查同一标识是否存在重复/乱序；最后检查配置和脚本版本。解析 JSON 出现 `<` 时，应保留上游 HTTP status/body，可能是 HTML 403 而非 JSON 格式问题。

## 安全与限制

日志不能输出账号密码、token、完整敏感报文。当前代码无法确认生产日志保留期、采样与告警平台。来源：AOP、BusinessLog/SysOperationLog、业务发送日志、各 Job/Callback。面试追问：分布式 tracing 与业务相关键各自价值、日志与审计快照的差异。

## 按业务域选择真源

Booking/Release 先查 `biz_advance_booking` 与 ext，再查 customer task、监听表和 release history；Bill Input 先查 `biz_bill_record`，再查 submit-check、file record、schedule job 和 Mongo 详情；VGM Intake 查 `vgm_info`/`vgm_detail`，通道执行另查 `biz_vgm_record`；BL 查 `bl_entrusted_info`、`bl_work_order` 和操作快照。选错真源会把历史、投影或下游记录误当当前事实。

## 时间线判读

数据库 `updated_at`、Job 调度时间、外部回执时间和日志时间可能来自不同主机/时区。先统一时区，再按因果标识排序。回调时间早于本地日志不一定表示乱序，可能是外部事件时间；真正的乱序要看旧状态条件和业务版本是否被拒绝。

## 典型错误分类

| 错误 | 证据 | 处理方向 |
| --- | --- | --- |
| 配置不支持 | carrier/channel/key、解析结果 | 修配置或明确能力边界，不盲目重试 |
| 账号不可用 | account id、状态、归属 | 账号治理，不改业务状态伪装成功 |
| 外部协议失败 | HTTP status/body/business code | 保留原响应，按可重试性分类 |
| 幂等/状态冲突 | taskNo、旧/新状态、条件更新行数 | 判断重复/晚到，不直接覆盖 |
| 多存储漂移 | MySQL/Mongo/fileKey 对照 | 补偿缺失一侧，保留审计 |
| 调度积压 | handler、参数、最老任务时间 | 查 executor/扫描范围/单次耗时 |

一次诊断应找到“最早偏离预期的不变量”，而不是只解释最后抛出的异常。
