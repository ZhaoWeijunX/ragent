---
module: log-statistics-bi-chart-and-observability
title: 日志统计 BI 图表集成配置
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 集成与配置

首页统计入口 `/api/v1/statistics/homepage/{today,all,runtime,useCount}` 依赖 `HomepageStatisticsQueryParam` 和 `CycleTypeEnum`。BI 查询通过 `BiQueryDTO` 解析 begin/end；日聚合会向前多取一天计算环比再删除首桶，周/月会对齐周一或上月一号。`BiDataServiceImpl` 以 `tenant_id`、日期字段、parent_route/carrier/user/status 构建 ES 查询，size=0，仅消费聚合结果。

图表读取 `CustomerChartPerm`、`RoleChartPerm`、`UserChartPerm`，按 cid 严格查询，但角色/用户 chartId 字符串解析异常会导致请求失败。操作日志需配置 `desk.sys-operation-log.enabled`，快照由 Mongo 服务保存，日志行由 MyBatis 服务保存。源清单：Homepage controller、StatisticsManager、BiDataService/EsTemplate、ChartService/permission mappers、log config。线上 ES mapping 与刷新周期当前代码无法确认。

## 时间窗口与 ES 查询契约

`BizHomepageStatisticsController` 在 today 缺少日期时补当天起止；runtime 缺任一时间或 queryType 时重置为最近 7 个完整自然日。调用方若自行传时间窗口，需要保证 begin/end 与 `CycleTypeEnum` 一致，否则首页曲线和 BI 曲线不会可比。`BiDataServiceImpl` 会对日、周、月分别重置边界：日趋势额外取前一日，周趋势对齐上一个周一，月趋势对齐上月一号；返回时删除只用于环比的首桶。

BI 请求由 `BiQueryDTO` 携带 indices、dataType、aggregationType 和业务筛选条件，`EsTemplate.query` 的 source size 固定为 0，响应只消费 aggregation。集成方必须把 `tenant_id` 作为查询真边界，并明确 index 名字和日期字段；不能把 `sys_operation_log` 的 MySQL 行直接当作 BI 索引输入。切换 dataType 会同时改变聚合字段和计数实现，因此同一筛选条件下的数值不可不加说明地横向比较。

## 权限与异常处理

图表权限读取顺序不是覆盖关系，而是集合并集：全用户配置、单角色行和单用户行命中的 chartId 都会保留。角色或用户表中的空串、非数字、重复逗号会进入 split/parse 路径，当前代码没有在 `ChartServiceImpl` 中做容错；配置发布必须先做格式校验。验证应覆盖无权限用户、仅角色权限、仅用户权限、两者叠加、坏 chartId 和 ES 无桶五类情况。生产索引权限、跨租户 alias 和刷新延迟仍需运行态确认。
