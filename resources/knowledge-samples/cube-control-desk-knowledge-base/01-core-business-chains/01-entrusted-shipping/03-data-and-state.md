---
title: SHIPPING 数据模型与状态边界
module: entrusted-shipping
doc_type: data-and-state
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# SHIPPING 数据模型与状态边界

## 证据合同与实现核验

本文供排查“页面有单但查询不到/状态错位”；非目标是定义所有业务状态。主数据证据是 `entrusted_work_order`、`entrusted_info`，来源是 `entrusted_mail_record`/`entrusted_chat_record`，协作关系是 `entrusted_work_order_collaborator`；共享来源表必须带 `work_order_type` 语义过滤。实体证据：`EntrustedWorkOrder`、`EntrustedInfo`、`IEntrustedRecord` 及 `biz/core/entrusted` Mapper/XML。

状态更新调用方为 `WorkOrderCreateController`、`WorkOrderManagerImpl`，被调用方为对应 service；`UserContext` 注入租户/用户，`RedisClientWrap`/`RedisKeyConstant` 仅能作为缓存/并发证据，不能替代数据库真源。事务覆盖转交、协作和部分状态更新；跨库一致性、缓存失效时序和线上隔离级别当前代码无法确认。

代码/文档差异：本文不把缓存或通知描述为最终状态，也不把 BILL 主表混入 SHIPPING。测试为 `WorkOrderAllocationManagerImplTest`、`WorkOrderPageQueryServiceImplTest`；未知项与源码列表如上；最后验证日期 2026-08-26。

## 核心数据

| 数据 | 当前用途 |
|---|---|
| `entrusted_mail_record` | 接单邮件及其处理上下文；通过 `work_order_type` 与业务域隔离 |
| `entrusted_chat_record` | 对话来源记录；同样需要按工单类型隔离 |
| `entrusted_work_order` | SHIPPING 工单主记录 |
| `entrusted_info` | 托书/委托业务信息 |
| `entrusted_work_order_collaborator` | 工单协作关系 |
| `entrusted_customer_agent_config` | 客户、Agent 与工单类型相关配置 |
| `entrusted_record_valid_rule` | 记录有效性规则，需按工单类型读取 |

## 状态观察原则

当前代码中邮件处理至少存在 `PENDING`、`PROCESSING` 等 Agent 状态分支；等待处理与处理超时分别由两个 Job 扫描。工单的接单、审核、关闭等业务状态应以 `WorkOrderController` 调用的 Manager/Service 实现和枚举为准，不能从旧 wiki 的文字推断完整状态机。

## 隔离规则

共享记录表不是共享业务语义。查询或修改邮件、聊天、客户 Agent 配置、有效性规则时必须保留 `work_order_type` 条件；SHIPPING 主数据不能替换为 BILL 的 `bl_*` 主表。

## 未确认项

本文不列出数据库字段全集，也不把历史设计中的状态迁移视为当前契约；新增状态时应同步检查实体、枚举、Service、Controller 和 api-test 场景。
