---
title: SHIPPING 接单与托书工单模块概览
module: entrusted-shipping
doc_type: module-overview
audience: new-backend
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# SHIPPING 接单与托书工单模块概览

## 证据合同与实现核验

**目的/读者/非目标**：供接单域后端定位 SHIPPING 责任边界；不描述 BILL 主数据、Booking 执行器或 Release 监听器。

**已核验链路**：`WorkOrderCreateController#createWorkOrder` 取 `IEntrustedRecord`，通过 `WorkOrderTypeParser`/`EntrustedOrderCreateFactory` 分派到 SHIPPING `OrderCreate`，再由 `EntrustedWorkOrderService`、`EntrustedInfoService` 持久化。操作侧由 `WorkOrderController` → `WorkOrderManagerImpl`；共享上下文由 `WorkOrderContextResolverRegistry` 隔离。

**关键证据**：`WorkOrderCreateController.java`、`WorkOrderManagerImpl.java`、`AbstractShippingOrderCreate.java`、`ShippingWorkOrderContextResolver.java`、`EntrustedWorkOrder.java`、`EntrustedInfo.java`，以及 `biz/core/entrusted` Mapper/XML。邮件/聊天来源分别是 `EntrustedMailRecord`/`EntrustedChatRecord`。

**代码/文档差异**：历史资料把 `WorkOrderManagerImpl` 泛化为所有工单管理器；当前类注释和 resolver 证明其主要是 SHIPPING 管理。`@ApiPermission(false)` 只说明该入口标注的接口策略，不等于后端业务操作无权限校验。

**未知项**：外部 Agent SLA、邮件供应商投递保证、跨服务事务与线上隔离级别当前代码无法确认。

**测试/运行证据**：`WorkOrderContextResolverRegistryTest`、`WorkOrderPageQueryServiceImplTest`、`WorkOrderAllocationManagerImplTest`；运行态场景位于 `../api-test/scenarios/entrusted`，需现场环境验证。

## 模块职责

SHIPPING 是原有托书接单工单链路。当前代码以 `work_order_type=SHIPPING` 区分该域；邮件或对话记录进入 `entrusted_mail_record`、`entrusted_chat_record` 后，由 Agent/接单编排生成 `entrusted_work_order` 与 `entrusted_info`，再由操作员执行接单、审核、去订舱、关闭、复制或重试等动作。

## 代码分层

- HTTP 入口：`cube-control-desk-app/.../entrusted/controller/WorkOrderCreateController.java`、`WorkOrderController.java`。
- 业务编排：`cube-control-desk-biz/.../entrusted/manager/WorkOrderManagerImpl.java`。
- 业务数据：`EntrustedWorkOrderServiceImpl`、`EntrustedInfoServiceImpl`、`EntrustedMailRecordServiceImpl`。
- 自动接单：`EntrustedBookingEmailJob`、`EntrustedBookingWaitProcessEmailJob`、`EntrustedBookingProcessingEmailJob`。
- 建单策略：`AbstractShippingOrderCreate`；邮件记录操作：`MailRecordOperator`。

## 重要边界

SHIPPING 与 BILL 共用部分邮件、模板和配置基础设施，但不共用工单主表。共享工单入口应通过 `WorkOrderContextResolverRegistry` 分派，不能把 BILL 行为直接塞入 `WorkOrderManagerImpl` 或 `entrusted_work_order`。

## 证据来源

主要来源为上述当前 Java 实现、`doc/onboarding/entrusted-quick-start.md`、`doc/wiki/module-entrusted.md` 与仓库业务索引。旧设计中未在代码核验的内容不视为当前实现。
