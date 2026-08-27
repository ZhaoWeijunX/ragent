---
title: 平台支撑能力模板
capability: documentation-governance
status: reference
last_verified: 2026-08-26
---

# 支撑能力文章模板

每个能力至少两篇：`01-capability-overview.md`、`02-integration-and-configuration.md`；有独立运行或运维行为时增加 `03-troubleshooting-and-verification.md`。

## 能力概览应回答

- 解决什么跨业务问题，哪些模块消费。
- 能力的所有权和非目标，不能吞并调用方业务。
- 入口、抽象接口、实现选择、数据与下游。
- 运行时完整链路和 Mermaid 图。
- 并发、事务、一致性、异常和技术原理。
- 当前风险、限制、面试追问、差异与未知项。

## 接入与配置应回答

- 调用方需要提供哪些 tenant/carrier/channel/source/task 信息。
- Spring profile、sys/tenant/carrier/account/script 等配置解析顺序。
- 接口/Strategy/Registry/AutoConfig 的扩展方法。
- 默认值、兼容性、缓存刷新、安全和回滚。
- 最小代码/配置/SQL/test 改动面。

## 排障与验证应回答

- 入口到结果各阶段的可观察证据和相关键。
- 常见错误分类、重复/超时/积压/配置缺失边界。
- 可安全执行的静态、单测、集成/api-test 和运行检查。
- 外部系统或生产环境无法确认项。

## 统一证据块

每篇包含目的与读者、范围/非目标、关键代码路径、数据配置状态、调用方/下游、验证证据、文档差异、未知项、来源和 `last_verified`。
