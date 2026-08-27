---
title: SHIPPING API 与跨模块协作
module: entrusted-shipping
doc_type: api-and-collaboration
audience: backend-frontend-testing-ops
status: initial-verified
source_policy: current-repository-docs-and-code-only
last_verified: 2026-08-26
---
# SHIPPING API 与跨模块协作

## 证据合同与实现核验

目的为帮助前后端及上下游定位契约；非目标是替代 OpenAPI 文档。入口证据：`WorkOrderCreateController` 的 `/api/v1/workOrder/v2/create`、`/createCorrespondence`、配置查询，`WorkOrderController` 的列表/详情/接单/关闭/转交，`EntrustedMailRecordController` 的邮件操作。请求/响应由 `CreateWorkOrderRequestV2DTO`、`CreateWorkOrderResponseDTO`、`WorkOrderOperateParamDTO` 定义。

调用链为 Web/邮件处理 → Controller → `EntrustedRecordManager`、配置 service、Factory/Resolver → SHIPPING strategy → service/mapper；转交还调用 `CompanyStaffInfoConsumer`、`IStaffRoleV2Consumer`、`NoticeManager`。当前代码明确普通员工只能操作本人跟进单、直接转交仅一人、协同目标 1–20 人。

代码/文档差异：页面管理员路由不是后端授权事实；通知可靠投递和外部 Agent 协议当前代码无法确认。测试为 resolver、page query、allocation 三类单测；源码列表为上述 controller/DTO/manager/consumer；最后验证日期 2026-08-26。

## HTTP 入口

`WorkOrderCreateController` 的基路径为 `/api/v1/workOrder/v2`，当前可见建单入口包括 `/create`、`/createCorrespondence`、`/getParseConfig`。`WorkOrderController` 的基路径为 `/api/v1/workOrder`，提供 `/pageV2`、`/claim`、`/acceptCommission`、`/submitReview`、`/goBooking`、`/retryBooking`、`/close`、`/copy` 等操作。

## 协作关系

- 前端提交建单参数，Controller 负责校验和请求装配，Manager/策略负责业务编排。
- Agent/邮件 Job 产生或推进记录；操作员 API 处理人工接单和审核。
- `/goBooking` 是接单域到订舱域的协作入口，后续任务状态与回调由 Booking 链路负责。
- `../api-test/scenarios/entrusted/email_mapping_api_test.yaml` 等场景可作为运行态证据，但不能代替代码契约。

## 变更边界

涉及共享工单入口时，先确认 `work_order_type` 和 `WorkOrderContextResolverRegistry` 的分派；涉及字段配置时，先阅读 `doc/onboarding/entrusted-field-configuration-contract.md`。前端可见按钮不等于后端授权，具体权限需追 Controller/Service 实现。
