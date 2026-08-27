---
module: log-statistics-bi-chart-and-observability
title: 日志统计 BI 图表排障验证
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 排障与验证

统计为空先核对 queryStart/End、时区 `+08:00`、queryType/aggregationType、tenant_id 与索引名；环比异常检查额外首日和周/月边界。图表无权限检查 cid、roleId、userId 三张权限表及逗号 chartId。审计缺失检查切面开关、方法注解和 snapshot/log 两端。

静态定位：`rg -n "homepageToday|queryOrderStatistDimension|EsTemplate|getPermittedCharts|SysOperationLogAspect"`；BI SQL/DDL 只能证明表结构，不能证明 ES 数据已刷新。本次未执行 ES、生产统计接口或图表前端验证，未知项包括指标口径、索引保留期和告警阈值。源清单：统计/BI/Chart 服务、Controller、权限 Mapper、日志切面。

## 从采集到展示的核对顺序

应先区分三类数据：Web/Job 调用日志用于追踪一次执行，`sys_operation_log` 与 Mongo snapshot 用于审计业务动作，统计/BI 服务则面向聚合指标。它们可能共享 traceId、tenantId 或业务 ID，但写入时机、存储和一致性等级不同。页面图表为空时，先证明数据是否进入来源索引/表，再检查聚合时间窗，最后检查图表权限；直接修改前端查询条件容易掩盖采集缺口。

| 层次 | 核对项 | 常见误判 |
| --- | --- | --- |
| 采集 | `WebLogAop`、`TraceXxlJobAop`、操作日志切面是否进入 | 有应用日志不代表有业务审计快照 |
| 存储 | ES 索引、MySQL 日志表、Mongo snapshot | DDL 存在不代表索引已建或数据已刷新 |
| 聚合 | queryStart/End、`+08:00`、日周月边界、租户过滤 | 当日为空不一定是无数据，可能是时区偏移 |
| 权限 | cid/roleId/userId 与 chartId 映射 | 后端有数据不代表当前用户可见 |
| 展示 | queryType、aggregationType、单位和舍入 | 环比异常可能来自口径而非计算错误 |

回归验证至少选一条可追踪业务操作，从入口 trace 到日志/快照，再核对统计接口是否在预期时间窗聚合，最后以不同角色验证图表权限。当前代码无法确认生产 ES 刷新延迟、索引生命周期、指标口径审批和告警策略；这些不能写成已验证能力。
