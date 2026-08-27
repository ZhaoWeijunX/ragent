---
module: log-statistics-bi-chart-and-observability
title: 日志统计 BI 图表与可观测性概览
status: source-verified
last_verified: 2026-08-26
source_policy: current-repository-code-config-and-sql
---

# 日志统计 BI 图表与可观测性

能力由三层组成：业务操作日志（`SysOperationLogAspect`）、首页统计（`BizHomepageStatisticsController`→`StatisticsManager`）、BI/图表（`BiDataServiceImpl`、`ChartServiceImpl`）。首页提供 today/all/runtime/useCount；缺少日期时 today 默认当天，runtime 默认最近 7 个完整日并按 DAY 聚合。BI 使用 `EsTemplate` 对租户索引构造 BoolQuery 和日期/terms/cardinality 聚合，支持 route、carrier、user、worker/order 状态过滤，并计算环比。

图表权限按 cid 合并客户全局、角色逗号分隔 chartId、用户 chartId 三类集合。操作日志切面在业务成功后保存 before/after 差异；它是审计证据，不是指标事实源。ES、MySQL、Mongo 各自承载不同数据，当前代码无法确认索引生命周期、指标刷新延迟和监控平台告警接入。源清单：StatisticsController/Manager、BiDataServiceImpl、ChartServiceImpl、log aspect、BI SQL。

## 两条统计链不能混用

首页统计是面向运营概览的同步查询。`StatisticsManager#homepageToday` 汇总完成任务和待处理任务；`homepageAll` 从归档数据和任务时长服务计算任务总量、总时长，并使用固定公式换算 accumulated cost；`homepageRuntime` 把秒转为小时；`homepageUseCount` 按指令 ID 聚合后再映射 `SysCommandEntity.name`。这些结果不等同于 ES BI 指标，也不以操作审计日志为来源。

`BiDataServiceImpl#queryOrderStatistDimension` 是另一条 ES 聚合链。它按 dataType 选择 `work_order_create_date` 或 `info_create_date`，使用 `+08:00`、DAY/WEEK/MONTH 日历桶和 `extendedBounds` 补空桶。工单维度是 `work_order_id` cardinality（precisionThreshold=10000），信息维度是 id value count；为计算环比，查询会包含前一周期，再在结果中删除首桶。前值为 0 时当前实现把环比设为 100%，这是一项展示口径，不应在文档中写成数学上的无歧义增长率。

`ChartServiceImpl#getPermittedCharts` 以 Set 合并客户全局、角色和用户权限；角色/用户的 chartId 以逗号分隔字符串保存并 `Integer.parseInt`。该设计使授权来源可叠加，但坏格式会在读取时失败，且“有 ES 数据”不代表当前用户被授权看到图表。

## 证据与未知

日志切面、首页统计、ES BI、图表权限分别有不同写入时机和租户条件。排查指标差异时应先确定在看哪一条链，再追数据源而不是在前端互相替代。当前代码无法确认 ES alias/mapping、归档任务刷新频率、成本公式的业务口径审批和生产告警平台接入；这些是运维或产品确认项。
